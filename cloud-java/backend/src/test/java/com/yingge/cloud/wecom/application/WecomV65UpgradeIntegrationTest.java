package com.yingge.cloud.wecom.application;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class WecomV65UpgradeIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("wecom_upgrade").withUsername("test").withPassword("synthetic-password");

    @Test void v64UpgradePreservesCustomerGrantAndBindingAndLimitsLongLivedClient() {
        var baseline = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("64").load();
        baseline.migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        UUID org=UUID.randomUUID(), user=UUID.randomUUID(), member=UUID.randomUUID(), customer=UUID.randomUUID(), grant=UUID.randomUUID();
        jdbc.update("insert into organizations(id,name,code) values(?,?,?)",org,"Synthetic Upgrade",org.toString());
        jdbc.update("insert into user_accounts(id,username,password_hash,display_name) values(?,?,?,?)",user,user.toString(),"unused","合成测试成员");
        jdbc.update("insert into memberships(id,organization_id,user_id) values(?,?,?)",member,org,user);
        jdbc.update("insert into customers(id,organization_id,owner_membership_id,created_by_membership_id,visibility,full_name,phone,identity_type,intention_level,urgency_level,verification_status,current_status) values(?,?,?,?,'PRIVATE','合成保留客户','synthetic','在校生','中','中','未核实','初次接触')",customer,org,member,member);
        jdbc.update("insert into wecom_ai_bot_identity_bindings(id,organization_id,membership_id,user_account_id,wecom_userid) values(?,?,?,?,?)",UUID.randomUUID(),org,member,user,"synthetic-wx-user");
        jdbc.update("insert into oauth_clients(id,client_id,client_name,redirect_uris,grant_types,response_types) values(?,'synthetic-old-client','Synthetic','[]','[]','[]')",UUID.randomUUID());
        jdbc.update("insert into oauth_grants(id,client_id,user_id,membership_id,organization_id,resource_uri,scopes,expires_at) values(?,'synthetic-old-client',?,?,?,'http://localhost/mcp','[\"yingge.read\"]',now()+interval '30 days')",grant,user,member,org);
        var expiry=jdbc.queryForObject("select expires_at from oauth_grants where id=?",java.sql.Timestamp.class,grant);
        var upgraded=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .locations("classpath:db/migration").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("select full_name from customers where id=?",String.class,customer)).isEqualTo("合成保留客户");
        assertThat(jdbc.queryForObject("select active from wecom_ai_bot_identity_bindings where membership_id=?",Boolean.class,member)).isTrue();
        assertThat(jdbc.queryForObject("select expires_at from oauth_grants where id=?",java.sql.Timestamp.class,grant)).isEqualTo(expiry);
        assertThatThrownBy(()->jdbc.update("update oauth_grants set expires_at=null where id=?",grant)).hasMessageContaining("oauth_long_lived_internal_client_check");
        jdbc.update("insert into oauth_clients(id,client_id,client_name,redirect_uris,grant_types,response_types) values(?,'yingge_wecom_agent_internal','Internal','[]','[]','[]')",UUID.randomUUID());
        jdbc.update("update oauth_grants set client_id='yingge_wecom_agent_internal',expires_at=null where id=?",grant);
        assertThat(jdbc.queryForObject("select expires_at from oauth_grants where id=?",java.sql.Timestamp.class,grant)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from wecom_agent_settings",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from customer_deal_bonus_ledger",Integer.class)).isZero();
    }
}
