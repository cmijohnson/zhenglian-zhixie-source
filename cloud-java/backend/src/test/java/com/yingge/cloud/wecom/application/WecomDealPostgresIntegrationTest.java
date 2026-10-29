package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.api.CustomerDtos;
import com.yingge.cloud.customer.application.CustomerService;
import com.yingge.cloud.customer.infrastructure.CustomerEntity;
import com.yingge.cloud.deal.application.DealConfirmationService;
import com.yingge.cloud.permission.application.PermissionService;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.permission.domain.RoleCode;
import com.yingge.cloud.shared.api.ApiException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real Flyway/PostgreSQL locks, drafts, ledger and outbox; customer visibility is supplied by a scoped fixture. */
@Testcontainers
class WecomDealPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("wecom_deal_test").withUsername("test").withPassword("test-password");
    static final ObjectMapper JSON=new ObjectMapper();
    static final WecomAgentCipher CIPHER=new WecomAgentCipher(Base64.getEncoder().encodeToString(new byte[32]));
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    UUID org,member,customer;

    @BeforeAll static void migrations(){
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }
    @BeforeEach void setup(){
        var datasource=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc=new JdbcTemplate(datasource);
        tx=new TransactionTemplate(new DataSourceTransactionManager(datasource));
        org=UUID.randomUUID();
        jdbc.update("insert into organizations(id,name,code) values(?,?,?)",org,"Synthetic Wecom",org.toString());
        member=member("Synthetic Member");
        customer=customer(member,"Synthetic Customer");
        jdbc.update("insert into wecom_agent_settings(organization_id,enabled,submission_chat_id,broadcast_chat_id,full_data_risk_accepted_at) values(?,true,'business','broadcast',now())",org);
    }

    @Test void persistedMixedCommandsConfirmWithoutAnyFeishuConfiguration() throws Exception {
        var fixture=fixture(member);
        seed(member,customer);
        assertThat(run(fixture,"/set primary=@wx_"+member+" order=3000 bonus=600")).contains("订单：¥3000");
        String oldToken=state(member).previewToken;
        // A second service instance reloads the same encrypted draft, like a natural-language callback.
        var restored=fixture(member);
        run(restored,"/order 3200");
        assertThatThrownBy(()->run(restored,"/confirm-token "+oldToken)).isInstanceOf(ApiException.class);
        String token=state(member).previewToken;
        assertThat(run(restored,"/confirm-token "+token)).contains("成单已确认");
        assertThat(count("customer_deal_requests")).isEqualTo(1);
        assertThat(count("customer_deal_bonus_ledger")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select confirmation_channel from customer_deal_requests where organization_id=?",String.class,org)).isEqualTo("WECOM_BOT");
        assertThat(jdbc.queryForObject("select count(*) from feishu_deal_message_outbox where organization_id=?",Integer.class,org)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from feishu_deal_bot_settings where organization_id=?",Integer.class,org)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from wecom_ai_bot_message_outbox where organization_id=?",Integer.class,org)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select details from interactions where organization_id=?",String.class,org)).contains("企业微信");
    }

    @Test void threeRevisionsExpiredPreviewAndCancelledCardCannotWrite() throws Exception {
        var fixture=fixture(member);seed(member,customer);
        run(fixture,"/set primary=@wx_"+member+" order=3000 bonus=600");
        String original=state(member).previewToken;
        run(fixture,"/order 3100");run(fixture,"/order 3200");run(fixture,"/order 3300");
        assertThatThrownBy(()->run(fixture,"/order 3400")).isInstanceOf(ApiException.class);
        var state=state(member);state.previewExpiresAt=Instant.now().minusSeconds(1).toString();persist(member,state);
        assertThatThrownBy(()->run(fixture,"/confirm-token "+state.previewToken)).isInstanceOf(ApiException.class);
        run(fixture,"/cancel");seed(member,customer);
        run(fixture,"/set primary=@wx_"+member+" order=3000 bonus=600");
        assertThatThrownBy(()->run(fixture,"/confirm-token "+original)).isInstanceOf(ApiException.class);
        assertThat(count("customer_deal_requests")).isZero();
    }

    @Test void concurrentConfirmationsProduceOneDealAndOneBroadcast() throws Exception {
        UUID second=member("Synthetic Second");
        var firstFixture=fixture(member);var secondFixture=fixture(second);
        seed(member,customer);seed(second,customer);
        run(firstFixture,"/set primary=@wx_"+member+" order=3000 bonus=600");
        run(secondFixture,"/set primary=@wx_"+second+" order=3000 bonus=600");
        String one=state(member).previewToken,two=state(second).previewToken;
        CountDownLatch start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->confirmAfter(start,firstFixture,one));
            var other=executor.submit(()->confirmAfter(start,secondFixture,two));
            start.countDown();
            assertThat((first.get()?1:0)+(other.get()?1:0)).isEqualTo(1);
        }
        assertThat(count("customer_deal_requests")).isEqualTo(1);
        assertThat(count("customer_deal_bonus_ledger")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from wecom_ai_bot_message_outbox where organization_id=?",Integer.class,org)).isEqualTo(1);
    }

    @Test void idempotencyPayloadMismatchIsRejectedAndPendingWebRequestIsReused() throws Exception {
        UUID pending=UUID.randomUUID();
        jdbc.update("insert into customer_deal_requests(id,organization_id,customer_id,requested_by_membership_id,status,source,idempotency_key_hash) values(?,?,?,?,'PENDING','DIRECT',?)",
                pending,org,customer,member,"0".repeat(64));
        var fixture=fixture(member);
        var command=new DealConfirmationService.FeishuConfirmation(customer,member,List.of(),300000,60000,
                Map.of(member,60000L),"integration-key-stable");
        UUID result=tx.execute(status->fixture.deals.confirmFromWecom(command));
        assertThat(result).isEqualTo(pending);
        UUID replay=tx.execute(status->fixture.deals.confirmFromWecom(command));
        assertThat(replay).isEqualTo(pending);
        var conflicting=new DealConfirmationService.FeishuConfirmation(customer,member,List.of(),400000,60000,
                Map.of(member,60000L),"integration-key-stable");
        assertThatThrownBy(()->tx.execute(status->fixture.deals.confirmFromWecom(conflicting)))
                .isInstanceOf(ApiException.class).hasMessageContaining("幂等键");
        assertThat(count("customer_deal_bonus_ledger")).isEqualTo(1);
    }

    @Test void repeatedQuickCreateDoesNotDuplicateAndTransactionRollbackPreservesOriginalCount() throws Exception {
        var fixture=fixture(member);
        run(fixture,"/new Synthetic Quick");
        assertThat(jdbc.queryForObject("select count(*) from customers where organization_id=? and full_name='Synthetic Quick'",Integer.class,org)).isEqualTo(1);
        assertThat(run(fixture,"/new Synthetic Quick")).contains("同名");
        run(fixture,"/cancel");
        assertThat(jdbc.queryForObject("select count(*) from customers where organization_id=? and full_name='Synthetic Quick'",Integer.class,org)).isEqualTo(1);
        assertThatThrownBy(()->tx.execute(status->{fixture.service.execute(org,member,"business","/new Rollback Customer");throw new IllegalStateException("synthetic failure");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from customers where organization_id=? and full_name='Rollback Customer'",Integer.class,org)).isZero();
    }

    private boolean confirmAfter(CountDownLatch start,Fixture fixture,String token)throws InterruptedException{
        start.await();try{run(fixture,"/confirm-token "+token);return true;}catch(ApiException exception){return false;}
    }
    private String run(Fixture fixture,String command){return tx.execute(status->fixture.service.execute(org,fixture.member,"business",command));}
    private int count(String table){return jdbc.queryForObject("select count(*) from "+table+" where organization_id=?",Integer.class,org);}

    private UUID member(String name){
        UUID user=UUID.randomUUID(),id=UUID.randomUUID();
        jdbc.update("insert into user_accounts(id,username,password_hash,display_name,password_change_required) values(?,?,?, ?,false)",user,user.toString(),"unused",name);
        jdbc.update("insert into memberships(id,organization_id,user_id) values(?,?,?)",id,org,user);
        jdbc.update("insert into membership_roles(membership_id,role_id) select ?,id from roles where code='MEMBER' and organization_id is null",id);
        jdbc.update("insert into wecom_ai_bot_identity_bindings(id,organization_id,membership_id,user_account_id,wecom_userid) values(?,?,?,?,?)",UUID.randomUUID(),org,id,user,"wx_"+id);
        return id;
    }
    private UUID customer(UUID owner,String name){
        UUID id=UUID.randomUUID();
        jdbc.update("insert into customers(id,organization_id,owner_membership_id,created_by_membership_id,visibility,full_name,phone,identity_type,intention_level,urgency_level,verification_status,current_status) values(?,?,?,?,'ORGANIZATION',?,'synthetic','在校生','中','中','未核实','初次接触')",id,org,owner,owner,name);
        return id;
    }
    private void seed(UUID actor,UUID id)throws Exception{var state=new WecomDealConversationService.State();state.customerId=id;persist(actor,state);}
    private void persist(UUID actor,WecomDealConversationService.State state)throws Exception{
        jdbc.update("insert into wecom_deal_conversations(id,organization_id,membership_id,chat_id,state_ciphertext,expires_at) values(?,?,?,'business',?,now()+interval '30 minutes') on conflict(organization_id,membership_id,chat_id) do update set state_ciphertext=excluded.state_ciphertext,expires_at=excluded.expires_at",
                UUID.randomUUID(),org,actor,CIPHER.encrypt(JSON.writeValueAsString(state)));
    }
    private WecomDealConversationService.State state(UUID actor)throws Exception{
        String payload=jdbc.queryForObject("select state_ciphertext from wecom_deal_conversations where organization_id=? and membership_id=? and chat_id='business'",String.class,org,actor);
        return JSON.readValue(CIPHER.decrypt(payload),WecomDealConversationService.State.class);
    }
    private Fixture fixture(UUID actorId){
        CurrentActorProvider actors=mock(CurrentActorProvider.class);
        var actor=new CurrentActor(UUID.randomUUID(),actorId,org,"test","Synthetic Member",Set.of(RoleCode.MEMBER),Set.of(),Set.of());
        when(actors.require()).thenReturn(actor);
        CustomerService customers=mock(CustomerService.class);
        when(customers.requireVisibleEntity(any(),any())).thenAnswer(invocation->{
            var entity=mock(CustomerEntity.class);
            return entity;
        });
        when(customers.get(any())).thenAnswer(invocation->response(invocation.getArgument(0)));
        when(customers.collaboration(any())).thenReturn(new CustomerDtos.CollaborationOverview(actorId,true,true,true,true,true,List.of(),List.of(),List.of()));
        when(customers.duplicateCheck(any())).thenReturn(new CustomerDtos.DuplicateResponse(false,false,0,List.of()));
        when(customers.createFromWecomQuickEntry(any(),any(),any())).thenAnswer(invocation->response(customer(invocation.getArgument(1),invocation.getArgument(0))));
        var policy=new WecomAgentPolicyService(jdbc,actors,mock(AuditService.class),CIPHER);
        var outbox=new WecomMessageOutbox(jdbc,CIPHER);
        var deals=new DealConfirmationService(jdbc,actors,new PermissionService(),customers,mock(AuditService.class),JSON,outbox,policy);
        return new Fixture(actorId,new WecomDealConversationService(jdbc,actors,customers,deals,JSON,CIPHER,policy),deals);
    }
    private CustomerDtos.Response response(UUID id)throws Exception{
        Map<String,Object> row=jdbc.queryForMap("select id,full_name,owner_membership_id,version from customers where id=? and organization_id=?",id,org);
        var tree=JSON.createObjectNode();tree.put("id",id.toString());tree.put("fullName",row.get("full_name").toString());
        tree.put("ownerMembershipId",row.get("owner_membership_id").toString());tree.put("ownerDisplayName","Synthetic Member");tree.put("version",((Number)row.get("version")).longValue());
        return JSON.treeToValue(tree,CustomerDtos.Response.class);
    }
    private record Fixture(UUID member,WecomDealConversationService service,DealConfirmationService deals){}
}
