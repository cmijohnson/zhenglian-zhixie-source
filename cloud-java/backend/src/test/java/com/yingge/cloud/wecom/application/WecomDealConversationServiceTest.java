package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.api.CustomerDtos;
import com.yingge.cloud.customer.application.CustomerService;
import com.yingge.cloud.customer.infrastructure.CustomerEntity;
import com.yingge.cloud.deal.application.DealConfirmationService;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.permission.domain.RoleCode;
import com.yingge.cloud.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WecomDealConversationServiceTest {
    @Test
    void moneyUsesExactCentsAndAcceptsLargeAmountsWithoutFloatingPoint() {
        assertThat(WecomDealConversationService.parseCents("3000.01",true)).isEqualTo(300001L);
        assertThat(WecomDealConversationService.parseCents("0.01",true)).isEqualTo(1L);
        assertThat(WecomDealConversationService.parseCents("0",false)).isZero();
        assertThat(WecomDealConversationService.parseCents("92233720368547758.07",true)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void malformedNegativeExponentialPrecisionAndOverflowAmountsAreRejected() {
        for(String amount : List.of("-1","1.001","1e3","NaN","1,000","","92233720368547758.08","0")) {
            assertThatThrownBy(()->WecomDealConversationService.parseCents(amount,true))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void completeDraftRequiresEveryParticipantAndExactSum() {
        var state=new WecomDealConversationService.State();
        state.customerId=UUID.randomUUID();
        state.primaryId=UUID.randomUUID();
        UUID assistant=UUID.randomUUID();
        state.assistantIds=List.of(assistant);
        state.orderCents=300000L;
        state.bonusCents=60000L;
        state.allocations=Map.of(state.primaryId,40000L,assistant,20000L);
        assertThat(WecomDealConversationService.complete(state)).isTrue();
        state.allocations=Map.of(state.primaryId,60000L);
        assertThat(WecomDealConversationService.complete(state)).isFalse();
        state.allocations=Map.of(state.primaryId,40000L,assistant,19999L);
        assertThat(WecomDealConversationService.complete(state)).isFalse();
        state.allocations=Map.of(state.primaryId,Long.MAX_VALUE,assistant,1L);
        assertThat(WecomDealConversationService.complete(state)).isFalse();
    }

    @Test
    void duplicatePeopleAndTooManyAssistantsCannotBecomeConfirmable() {
        var state=new WecomDealConversationService.State();
        state.customerId=UUID.randomUUID();
        state.primaryId=UUID.randomUUID();
        state.orderCents=100;
        state.bonusCents=100;
        state.allocations=Map.of(state.primaryId,100L);
        state.assistantIds=List.of(state.primaryId);
        assertThat(WecomDealConversationService.complete(state)).isFalse();
        state.assistantIds=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        assertThat(WecomDealConversationService.complete(state)).isFalse();
    }

    @Test
    void deterministicCommandsDeclareScopesWithoutGrantingAdministratorAccess() {
        assertThat(WecomDealConversationService.requiredScope("/confirm 12")).isEqualTo("yingge.write");
        assertThat(WecomDealConversationService.requiredScope("/new 张同学")).isEqualTo("yingge.write");
        assertThat(WecomDealConversationService.requiredScope("/leaderboard 2026-09")).isEqualTo("yingge.read");
        assertThat(WecomDealConversationService.requiredScope("/search 张同学")).isEqualTo("yingge.read");
    }

    @Test
    void slashPatchesAndRestoredDraftSharePreviewRevisionAndConfirmationState() throws Exception {
        var fixture=new Fixture();
        String response=fixture.execute("/set primary=@wx_member order=3000 bonus=600");
        assertThat(response).contains("/confirm 1","订单：¥3000","奖金：¥600");
        assertThat(fixture.state().firstCompleteAt).isNotNull();
        fixture.execute("/order 3200");
        assertThat(fixture.state().revisions).isEqualTo(1);
        assertThatThrownBy(()->fixture.execute("/confirm 1")).isInstanceOf(ApiException.class)
                .hasMessageContaining("版本已过期");
        verifyNoInteractions(fixture.deals);
        assertThat(fixture.execute("/confirm 2")).contains("成单已确认");
        verify(fixture.deals).confirmFromWecom(any());
        assertThat(fixture.execute("/confirm 2")).contains("已确认成单");
        verify(fixture.deals,times(1)).confirmFromWecom(any());
    }

    @Test
    void thirdRevisionIsLastAndCancelDoesNotDeleteFormalCustomer() throws Exception {
        var fixture=new Fixture();
        fixture.execute("/set primary=@wx_member order=3000 bonus=600");
        fixture.execute("/order 3100");
        fixture.execute("/order 3200");
        fixture.execute("/order 3300");
        assertThatThrownBy(()->fixture.execute("/order 3400")).isInstanceOf(ApiException.class)
                .hasMessageContaining("三次修订");
        fixture.execute("/cancel");
        assertThat(fixture.state().customerId).isNull();
        verifyNoInteractions(fixture.deals);
        verify(fixture.customers,never()).archive(any());
    }

    @Test
    void tabsAndUppercaseSlashUseTheSameDeterministicDraft() throws Exception {
        var fixture=new Fixture();
        assertThat(fixture.execute("/SET\tprimary=@wx_member order=3000 bonus=600")).contains("订单：¥3000");
        assertThat(fixture.execute("/ORDER\t3200")).contains("订单：¥3200");
        assertThat(fixture.state().revisions).isEqualTo(1);
    }

    @Test
    void expiredPreviewAndIdentityMismatchCannotConfirm() throws Exception {
        var fixture=new Fixture();
        fixture.execute("/set primary=@wx_member order=3000 bonus=600");
        var state=fixture.state();
        state.previewExpiresAt=Instant.now().minusSeconds(1).toString();
        fixture.seed(state);
        assertThatThrownBy(()->fixture.execute("/confirm 1")).isInstanceOf(ApiException.class)
                .hasMessageContaining("最新预览");
        assertThatThrownBy(()->fixture.service.execute(UUID.randomUUID(),fixture.member,"group","/confirm"))
                .isInstanceOf(ApiException.class).hasMessageContaining("身份");
        verifyNoInteractions(fixture.deals);
    }

    private static class Fixture {
        final UUID org=UUID.randomUUID(),member=UUID.randomUUID(),customer=UUID.randomUUID();
        final ObjectMapper json=new ObjectMapper();
        final WecomAgentCipher cipher=new WecomAgentCipher(Base64.getEncoder().encodeToString(new byte[32]));
        final CustomerService customers=mock(CustomerService.class);
        final DealConfirmationService deals=mock(DealConfirmationService.class);
        final MemoryJdbc jdbc=new MemoryJdbc();
        final WecomDealConversationService service;

        Fixture() throws Exception {
            CurrentActorProvider actors=mock(CurrentActorProvider.class);
            when(actors.require()).thenReturn(new CurrentActor(UUID.randomUUID(),member,org,"test","测试成员",
                    Set.of(RoleCode.MEMBER),Set.of(),Set.of()));
            var entity=mock(CustomerEntity.class);
            when(customers.requireVisibleEntity(any(),any())).thenReturn(entity);
            var response=json.readValue("{\"id\":\""+customer+"\",\"fullName\":\"合成客户\",\"ownerDisplayName\":\"测试成员\",\"version\":5}",CustomerDtos.Response.class);
            when(customers.get(customer)).thenReturn(response);
            var collaboration=new CustomerDtos.CollaborationOverview(member,true,true,true,true,true,
                    List.of(),List.of(),List.of());
            when(customers.collaboration(customer)).thenReturn(collaboration);
            when(deals.confirmFromWecom(any())).thenReturn(UUID.randomUUID());
            var policy=mock(WecomAgentPolicyService.class);
            when(policy.current(org)).thenReturn(new WecomAgentPolicyService.Policy(true,"group","broadcast",true,0));
            service=new WecomDealConversationService(jdbc,actors,customers,deals,json,cipher,policy);
            var state=new WecomDealConversationService.State();state.customerId=customer;seed(state);
        }
        String execute(String command){return service.execute(org,member,"group",command);}
        void seed(WecomDealConversationService.State state)throws Exception{jdbc.payload=cipher.encrypt(json.writeValueAsString(state));}
        WecomDealConversationService.State state()throws Exception{return json.readValue(cipher.decrypt(jdbc.payload),WecomDealConversationService.State.class);}

        class MemoryJdbc extends JdbcTemplate {
            String payload;
            @Override public <T> T queryForObject(String sql,Class<T> type,Object... args){
                if(type==String.class)return type.cast("测试成员");
                return null;
            }
            @Override public <T> List<T> query(String sql,RowMapper<T> mapper,Object... args){
                try {
                    ResultSet rs=mock(ResultSet.class);
                    if(sql.contains("from wecom_deal_conversations")){
                        when(rs.getObject("id",UUID.class)).thenReturn(UUID.randomUUID());
                        when(rs.getString("state_ciphertext")).thenReturn(payload);
                        when(rs.getTimestamp("expires_at")).thenReturn(Timestamp.from(Instant.now().plusSeconds(1800)));
                    }else if(sql.contains("wecom_ai_bot_identity_bindings")){
                        when(rs.getObject("id",UUID.class)).thenReturn(member);
                        when(rs.getString("display_name")).thenReturn("测试成员");
                        when(rs.getString("wecom_userid")).thenReturn("wx_member");
                    }else return List.of();
                    return List.of(mapper.mapRow(rs,0));
                }catch(Exception exception){throw new IllegalStateException(exception);}
            }
            @Override public int update(String sql,Object... args){
                if(sql.contains("insert into wecom_deal_conversations"))payload=(String)args[4];
                return 1;
            }
        }
    }
}
