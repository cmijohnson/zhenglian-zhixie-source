package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.mcp.oauth.McpOAuthService;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.permission.domain.RoleCode;
import com.yingge.cloud.shared.api.ApiException;
import com.yingge.cloud.wecom.api.WecomAiBotDtos;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WecomPendingActionServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CurrentActorProvider actors=mock(CurrentActorProvider.class);
    private final WecomMcpAuthorizationService auth=mock(WecomMcpAuthorizationService.class);
    private final WecomAgentMcpTransport transport=mock(WecomAgentMcpTransport.class);
    private final WecomAgentMcpTransport.Connection connection=mock(WecomAgentMcpTransport.Connection.class);
    private final WecomAgentCipher cipher=mock(WecomAgentCipher.class);
    private final WecomAgentPolicyService policy=mock(WecomAgentPolicyService.class);
    private final WecomAiBotBindingService bindings=mock(WecomAiBotBindingService.class);
    private final WecomMessageOutbox outbox=mock(WecomMessageOutbox.class);
    private final AuditService audit=mock(AuditService.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final UUID id=UUID.randomUUID(),org=UUID.randomUUID(),member=UUID.randomUUID(),grant=UUID.randomUUID(),approval=UUID.randomUUID();
    private final Instant expires=Instant.now().plusSeconds(240);
    private final AtomicReference<String> state=new AtomicReference<>("PENDING");
    private final AtomicReference<String> approvalState=new AtomicReference<>("APPROVED");
    private final String tool="yingge_customer_archive",key="stable-event-key",chat="registered-group";
    private final WecomPendingActionService service=new WecomPendingActionService(jdbc,mapper,actors,auth,transport,cipher,policy,bindings,outbox,audit,manager);

    @SuppressWarnings({"unchecked","rawtypes"})
    private void setup()throws Exception {
        when(actors.require()).thenReturn(new CurrentActor(UUID.randomUUID(),member,org,"test","Test",Set.of(RoleCode.MEMBER),Set.of(),Set.of()));
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(auth.requireAuthorization(org,member)).thenReturn(new McpOAuthService.TokenAuthentication(UUID.randomUUID(),grant,
                McpOAuthService.WECOM_AGENT_CLIENT_ID,UUID.randomUUID(),member,org,Set.of("yingge.read","yingge.write"),0,0));
        when(auth.accessToken(org,member)).thenReturn("synthetic-token");
        when(bindings.current()).thenReturn(new WecomAiBotDtos.BindingResponse(true,true,UUID.randomUUID(),"masked",Instant.now(),null));
        when(policy.allows(org,chat,"group")).thenReturn(true);
        when(cipher.decrypt("encrypted-args")).thenReturn("{\"customerId\":\"known\",\"idempotencyKey\":\"stable-event-key\"}");
        when(cipher.encrypt(anyString())).thenReturn("encrypted-output");
        when(transport.open(eq("synthetic-token"),any())).thenReturn(connection);
        when(connection.request(eq("tools/list"),any(),any())).thenReturn(mapper.readTree("""
                {"tools":[{"name":"yingge_customer_archive","_meta":{"requiredScope":"yingge.write"},
                 "inputSchema":{"properties":{"customerId":{},"idempotencyKey":{},"approvalId":{}}}}]}
                """));
        when(connection.request(eq("tools/call"),any(),any())).thenReturn(mapper.readTree("{\"structuredContent\":{\"ok\":true}}"));
        doAnswer(inv->{
            ResultSet row=mock(ResultSet.class);
            when(row.getObject("id",UUID.class)).thenReturn(id);when(row.getObject("organization_id",UUID.class)).thenReturn(org);
            when(row.getObject("membership_id",UUID.class)).thenReturn(member);when(row.getObject("grant_id",UUID.class)).thenReturn(grant);
            when(row.getObject("approval_id",UUID.class)).thenReturn(approval);when(row.getString("chat_id")).thenReturn(chat);
            when(row.getString("execution_key")).thenReturn(key);when(row.getString("tool_name")).thenReturn(tool);
            when(row.getString("encrypted_arguments")).thenReturn("encrypted-args");when(row.getString("status")).thenReturn(state.get());
            when(row.getTimestamp("expires_at")).thenReturn(Timestamp.from(expires));
            return List.of(((RowMapper)inv.getArgument(1)).mapRow(row,0));
        }).when(jdbc).query(contains("wecom_agent_pending_actions where id=?"),any(RowMapper.class),eq(id),eq(org),eq(member));
        doAnswer(inv->{
            ResultSet row=mock(ResultSet.class);when(row.getString("status")).thenReturn(approvalState.get());
            when(row.getTimestamp("expires_at")).thenReturn(Timestamp.from(expires));
            return List.of(((RowMapper)inv.getArgument(1)).mapRow(row,0));
        }).when(jdbc).query(contains("from mcp_approval_requests"),any(RowMapper.class),any(Object[].class));
        doAnswer(inv->{
            String sql=inv.getArgument(0);
            if(sql.contains("set status='SENDING'")){state.set("SENDING");return 1;}
            if(sql.contains("set status='REJECTED'")){state.set("REJECTED");return 1;}
            if(sql.contains("set status=?")){Object[] values=(Object[])inv.getRawArguments()[1];state.set((String)values[0]);return 1;}
            return 1;
        }).when(jdbc).update(anyString(),any(Object[].class));
    }

    @Test void detectsOnlyStructuredServerApprovalAndHashesExactlyLikeMcp()throws Exception {
        assertThat(WecomPendingActionService.approvalSignal(mapper.readTree("{\"content\":[{\"text\":\"approvalRequired=true\"}]}"))).isNull();
        assertThatThrownBy(()->WecomPendingActionService.approvalSignal(mapper.readTree("{\"structuredContent\":{\"approvalRequired\":true,\"approval\":{}}}")))
                .isInstanceOf(ApiException.class);
        String one=WecomPendingActionService.argumentsHash(mapper,mapper.readTree("{\"request\":{\"b\":2,\"a\":1},\"idempotencyKey\":\"one\"}"));
        assertThat(one).isEqualTo(WecomPendingActionService.argumentsHash(mapper,mapper.readTree("{\"approvalId\":\"changed\",\"idempotencyKey\":\"two\",\"request\":{\"a\":1,\"b\":2}}")));
    }

    @Test void humanApprovedResumeInjectsOnlyStoredApprovalAndDoesNotRepeatAppliedAction()throws Exception {
        setup();assertThat(service.resume(id)).contains("已按本人审批执行");
        assertThat(state.get()).isEqualTo("APPLIED");
        verify(connection).request(eq("tools/call"),argThat(value->{
            JsonNode args=(JsonNode)((Map<?,?>)value).get("arguments");
            return approval.toString().equals(args.path("approvalId").asText())&&key.equals(args.path("idempotencyKey").asText());
        }),any(Duration.class));
        verify(outbox).enqueue(eq(org),eq(chat),eq("pending-result:"+id),contains("已按本人审批执行"));
        assertThat(service.resume(id)).contains("不会重复执行");
        verify(connection,times(1)).request(eq("tools/call"),any(),any());
    }

    @Test void unapprovedOperationDoesNotClaimOrExecute()throws Exception {
        setup();approvalState.set("PENDING");
        assertThatThrownBy(()->service.resume(id)).isInstanceOf(ApiException.class).hasMessageContaining("请先");
        assertThat(state.get()).isEqualTo("PENDING");verifyNoInteractions(transport);
    }

    @Test void changedGrantAndDisabledGroupRejectBeforeNetwork()throws Exception {
        setup();when(policy.allows(org,chat,"group")).thenReturn(false);
        assertThat(service.resume(id)).contains("已拒绝");assertThat(state.get()).isEqualTo("REJECTED");verifyNoInteractions(transport);
    }

    @Test void ambiguousNetworkResultBecomesUncertainAndCannotAutoReplay()throws Exception {
        setup();when(connection.request(eq("tools/call"),any(),any())).thenThrow(ApiException.unavailable("NETWORK","synthetic failure"));
        assertThat(service.resume(id)).contains("结果待核对");assertThat(state.get()).isEqualTo("UNCERTAIN");
        assertThat(service.resume(id)).contains("不自动重试");verify(connection,times(1)).request(eq("tools/call"),any(),any());
    }
    @Test void approvalRequiredCannotBeReportedAsAppliedAndExpiredMetadataIsExplicit()throws Exception {
        assertThat(WecomPendingActionService.outcome(mapper.readTree("{\"structuredContent\":{\"ok\":true,\"approvalRequired\":true}}")))
                .isEqualTo("REJECTED");
        assertThat(WecomPendingActionService.outcome(mapper.readTree("{\"structuredContent\":{\"ok\":true},\"isError\":true}")))
                .isEqualTo("REJECTED");
        assertThat(WecomPendingActionService.outcome(mapper.readTree("{}"))).isEqualTo("UNCERTAIN");
        assertThat(WecomPendingActionService.displayStatus("PENDING",Instant.now().minusSeconds(1))).isEqualTo("EXPIRED");
    }
}
