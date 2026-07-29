package com.yingge.cloud.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4-alpine")
            .withDatabaseName("yingge_test")
            .withUsername("yingge_test")
            .withPassword("test-password");

    @Test
    void migratesFromZeroAndCreatesPersistentSessionAndTenantTables() throws Exception {
        var result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertThat(result.migrationsExecuted).isEqualTo(21);

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select count(*) from information_schema.tables where table_schema='public'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(25);
            }
            try (var roles = statement.executeQuery("select count(*) from roles where organization_id is null")) {
                assertThat(roles.next()).isTrue();
                assertThat(roles.getInt(1)).isEqualTo(4);
            }
            try (var sessions = statement.executeQuery("select count(*) from spring_session")) {
                assertThat(sessions.next()).isTrue();
            }
            try (var workflowTables = statement.executeQuery("""
                    select count(*) from information_schema.tables where table_schema='public'
                    and table_name in ('content_posts', 'daily_reports', 'work_tasks', 'work_task_assignments')
                    """)) {
                assertThat(workflowTables.next()).isTrue();
                assertThat(workflowTables.getInt(1)).isEqualTo(4);
            }
            try (var registrationCodes = statement.executeQuery("select count(*) from registration_codes")) {
                assertThat(registrationCodes.next()).isTrue();
            }
            try (var leaderIndex = statement.executeQuery("""
                    select count(*) from pg_indexes where schemaname='public'
                    and indexname='team_members_single_leader_idx'
                    """)) {
                assertThat(leaderIndex.next()).isTrue();
                assertThat(leaderIndex.getInt(1)).isEqualTo(1);
            }
            try (var announcements = statement.executeQuery("select count(*) from announcements")) {
                assertThat(announcements.next()).isTrue();
            }
            try (var leadershipFlag = statement.executeQuery("""
                    select show_on_leadership_screen
                    from announcements
                    limit 0
                    """)) {
                assertThat(leadershipFlag.getMetaData().getColumnCount()).isEqualTo(1);
            }
            try (var leadReviewColumns = statement.executeQuery("""
                    select count(*) from information_schema.columns where table_name='customers'
                    and column_name in ('lead_review_status','lead_review_reason',
                                        'lead_reviewed_by_membership_id','lead_reviewed_at')
                    """)) {
                assertThat(leadReviewColumns.next()).isTrue();
                assertThat(leadReviewColumns.getInt(1)).isEqualTo(4);
            }
            try (var assistantSettings = statement.executeQuery("select count(*) from assistant_settings")) {
                assertThat(assistantSettings.next()).isTrue();
            }
            try (var screenSettings = statement.executeQuery("select count(*) from leadership_screen_settings")) {
                assertThat(screenSettings.next()).isTrue();
            }
            try (var schoolDictionaryIndex = statement.executeQuery("""
                    select count(*) from pg_indexes where schemaname='public'
                    and indexname='uq_dictionary_school_label'
                    """)) {
                assertThat(schoolDictionaryIndex.next()).isTrue();
                assertThat(schoolDictionaryIndex.getInt(1)).isEqualTo(1);
            }
            try (var sourceDictionaryIndex = statement.executeQuery("""
                    select count(*) from pg_indexes where schemaname='public'
                    and indexname='uq_dictionary_source_label'
                    """)) {
                assertThat(sourceDictionaryIndex.next()).isTrue();
                assertThat(sourceDictionaryIndex.getInt(1)).isEqualTo(1);
            }
            try (var collaborationIndex = statement.executeQuery("""
                    select count(*) from pg_indexes where schemaname='public'
                    and indexname='uq_customer_member_access_grant'
                    """)) {
                assertThat(collaborationIndex.next()).isTrue();
                assertThat(collaborationIndex.getInt(1)).isEqualTo(1);
            }
            try (var conflictIndex = statement.executeQuery("""
                    select count(*) from pg_indexes where schemaname='public'
                    and indexname='idx_customers_name_major_conflict'
                    """)) {
                assertThat(conflictIndex.next()).isTrue();
                assertThat(conflictIndex.getInt(1)).isEqualTo(1);
            }
            try (var mcpTables = statement.executeQuery("""
                    select count(*) from information_schema.tables where table_schema='public'
                    and table_name in ('oauth_clients','oauth_authorization_requests','oauth_grants',
                      'oauth_authorization_codes','oauth_access_tokens','oauth_refresh_tokens',
                      'mcp_idempotency_records','mcp_approval_requests')
                    """)) {
                assertThat(mcpTables.next()).isTrue();
                assertThat(mcpTables.getInt(1)).isEqualTo(8);
            }
            try (var authVersion = statement.executeQuery("""
                    select count(*) from information_schema.columns where table_name='user_accounts'
                    and column_name='auth_version'
                    """)) {
                assertThat(authVersion.next()).isTrue();
                assertThat(authVersion.getInt(1)).isEqualTo(1);
            }
            try (var forumTables = statement.executeQuery("""
                    select count(*) from information_schema.tables where table_schema='public'
                    and table_name in ('forum_boards','forum_posts','forum_post_tags','forum_attachments',
                      'forum_comments','forum_likes','forum_announcement_reads','forum_reports',
                      'forum_moderation_actions')
                    """)) {
                assertThat(forumTables.next()).isTrue();
                assertThat(forumTables.getInt(1)).isEqualTo(9);
            }
            try (var friendFunnelColumns = statement.executeQuery("""
                    select count(*) from information_schema.columns where table_name='customers'
                    and column_name in ('friend_added_at','friend_trusted_at','friend_qualified_at')
                    """)) {
                assertThat(friendFunnelColumns.next()).isTrue();
                assertThat(friendFunnelColumns.getInt(1)).isEqualTo(3);
            }
            String organizationId = java.util.UUID.randomUUID().toString();
            statement.executeUpdate("""
                    insert into organizations (id, name, code)
                    values ('%s', '质量标签测试组织', 'quality-tag-test')
                    """.formatted(organizationId));
            try (var qualityTags = statement.executeQuery("""
                    select count(*) from tags
                    where organization_id='%s' and category='客户质量'
                      and name in ('A类用户','B类用户','C类用户')
                      and is_system=true and active=true
                    """.formatted(organizationId))) {
                assertThat(qualityTags.next()).isTrue();
                assertThat(qualityTags.getInt(1)).isEqualTo(3);
            }
        }
    }
}
