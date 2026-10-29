package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.application.CustomerService;
import com.yingge.cloud.deal.application.DealConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WecomAiBotIngressTest {
    @Test void wrongBotIdentityOrMissingSenderNeverReadsBusinessData() {
        var fixture=new Fixture();
        assertThat(fixture.service.handle(fixture.org,fixture.body("<@bot> /confirm").put("aibotid","other"))).isEmpty();
        var missing=fixture.body("<@bot> /confirm");missing.remove("from");
        assertThat(fixture.service.handle(fixture.org,missing)).isEmpty();
        verifyNoInteractions(fixture.policy,fixture.bindings,fixture.authorization,fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void broadcastAndUnregisteredGroupsNeverExecuteCommands() {
        var fixture=new Fixture();
        for(String group:List.of("broadcast","unregistered"))
            assertThat(fixture.service.handle(fixture.org,fixture.body("<@bot> /confirm").put("chatid",group))).isEmpty();
        verifyNoInteractions(fixture.bindings,fixture.authorization,fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void authenticatedUnregisteredGroupIsDiscoverableWithoutStoringMessageBody() {
        var fixture=new Fixture();
        assertThat(fixture.service.handleAuthenticatedCallback(fixture.org,
                fixture.body("<@bot> private customer message").put("chatid","unregistered"))).isEmpty();
        assertThat(fixture.jdbc.lastQuery).contains("wecom_ai_bot_event_inbox");
        assertThat(fixture.jdbc.lastQueryArgs).contains("unregistered","group","user");
        assertThat(fixture.jdbc.lastQueryArgs).doesNotContain("<@bot> private customer message");
        verifyNoInteractions(fixture.bindings,fixture.authorization,fixture.conversations,fixture.natural);
    }

    @Test void groupMessagesMustExplicitlyMentionThisBot() {
        var fixture=new Fixture();
        fixture.bound();
        for(String text:List.of("/confirm","给客户新增沟通","<@someone-else> /confirm"))
            assertThat(fixture.service.handle(fixture.org,fixture.body(text))).isEmpty();
        verifyNoInteractions(fixture.authorization,fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void unboundSenderAndGroupBindingNeverReachWriteServices() {
        var fixture=new Fixture();
        assertThat(fixture.service.handle(fixture.org,fixture.body("<@bot> /confirm"))).hasValueSatisfying(reply->assertThat(reply).contains("尚未绑定"));
        assertThat(fixture.service.handle(fixture.org,fixture.body("<@bot> /bind DEMO"))).hasValueSatisfying(reply->assertThat(reply).contains("私聊"));
        verify(fixture.bindings,never()).consumeCode(any(),anyString(),anyString());
        verifyNoInteractions(fixture.authorization,fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void duplicateMessageReturnsSavedReplyWithoutExecutingAgain() {
        var fixture=new Fixture();fixture.bound();fixture.jdbc.duplicate=true;
        fixture.jdbc.reply=new byte[]{1,2};
        when(fixture.cipher.decryptBytes(fixture.jdbc.reply)).thenReturn("已完成，结果已保存");
        assertThat(fixture.service.handle(fixture.org,fixture.body("<@bot> /confirm"))).hasValue("已完成，结果已保存");
        verify(fixture.authorization).requireScope(fixture.org,fixture.member,"yingge.write");
        verifyNoInteractions(fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void inFlightDuplicateDoesNotRetryBusinessWrite() {
        var fixture=new Fixture();fixture.bound();fixture.jdbc.duplicate=true;
        assertThat(fixture.service.handle(fixture.org,fixture.body("<@bot> /confirm"))).hasValueSatisfying(reply->assertThat(reply).contains("请勿重复提交"));
        verifyNoInteractions(fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void authenticatedBotScopedCallbackMayCarryAlreadyStrippedGroupMention() {
        var fixture=new Fixture();fixture.bound();fixture.jdbc.duplicate=true;
        fixture.jdbc.reply=new byte[]{1,2};
        when(fixture.cipher.decryptBytes(fixture.jdbc.reply)).thenReturn("已保存的成单结果");
        assertThat(fixture.service.handleAuthenticatedCallback(fixture.org,fixture.body("/confirm")))
                .hasValue("已保存的成单结果");
        verifyNoInteractions(fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void payloadCannotForgeTrustedCallbackFlag() {
        var fixture=new Fixture();fixture.bound();
        var body=fixture.body("/confirm").put("trusted",true).put("authenticatedCallback",true);
        assertThat(fixture.service.handle(fixture.org,body)).isEmpty();
        verifyNoInteractions(fixture.authorization,fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    @Test void malformedCardTaskCannotBecomeAConfirmationCommand() {
        var fixture=new Fixture();fixture.bound();
        var body=fixture.body("").put("msgtype","event");
        body.putObject("event").put("eventtype","template_card_event")
                .put("event_key","confirm:12345678-1234-1234-1234-123456789abc").put("task_id","different");
        assertThat(fixture.service.handle(fixture.org,body)).isEmpty();
        verifyNoInteractions(fixture.authorization,fixture.conversations,fixture.natural);
        assertThat(fixture.jdbc.updates).isZero();
    }

    private static final class Fixture {
        final UUID org=UUID.randomUUID(),member=UUID.randomUUID();
        final IngressJdbc jdbc=new IngressJdbc();
        final WecomAiBotBindingService bindings=mock(WecomAiBotBindingService.class);
        final WecomActorExecutor executor=mock(WecomActorExecutor.class);
        final WecomAgentPolicyService policy=mock(WecomAgentPolicyService.class);
        final WecomAgentCipher cipher=mock(WecomAgentCipher.class);
        final WecomMcpAuthorizationService authorization=mock(WecomMcpAuthorizationService.class);
        final WecomDealConversationService conversations=mock(WecomDealConversationService.class);
        final WecomNaturalLanguageService natural=mock(WecomNaturalLanguageService.class);
        final WecomAiBotCommandService service;
        Fixture() {
            var settings=new WecomAiBotSettings(true,"wss://openws.work.weixin.qq.com","bot","test",org.toString(),"https://example.test");
            service=new WecomAiBotCommandService(jdbc,mock(CurrentActorProvider.class),bindings,executor,
                    mock(CustomerService.class),mock(DealConfirmationService.class),settings,mock(WecomAiBotRuntimeStatus.class),
                    policy,cipher,authorization,conversations,natural,mock(PlatformTransactionManager.class));
            when(policy.allows(org,"business","group")).thenReturn(true);
            doAnswer(invocation->((Supplier<?>)invocation.getArgument(1)).get()).when(executor).as(eq(member),any());
        }
        void bound(){when(bindings.activeMembership(org,"user")).thenReturn(member);}
        ObjectNode body(String content) {
            var body=new ObjectMapper().createObjectNode().put("aibotid","bot").put("chattype","group")
                    .put("chatid","business").put("msgid","message-1").put("req_id","request-1").put("msgtype","text");
            body.putObject("from").put("userid","user");body.putObject("text").put("content",content);return body;
        }
    }
    private static final class IngressJdbc extends JdbcTemplate {
        boolean duplicate;byte[] reply;int updates;String lastQuery;Object[] lastQueryArgs;
        @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object...args) {
            lastQuery=sql;lastQueryArgs=args;
            if(!duplicate)return List.of();
            var row=mock(ResultSet.class);
            try {
                when(row.getObject(1,UUID.class)).thenReturn(UUID.randomUUID());
                when(row.getBytes(2)).thenReturn(reply);
                return List.of(mapper.mapRow(row,0));
            } catch(SQLException error){throw new IllegalStateException(error);}
        }
        @Override public int update(String sql,Object...args){updates++;return 1;}
    }
}
