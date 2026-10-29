package com.yingge.cloud.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.mcp.oauth.McpOAuthService;
import com.yingge.cloud.mcp.oauth.OAuthProtocolException;
import com.yingge.cloud.permission.domain.CurrentActor;
import com.yingge.cloud.permission.domain.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpWecomAuthorizationTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CurrentActorProvider actors=mock(CurrentActorProvider.class);
    private final AuditService audit=mock(AuditService.class);
    private final CurrentActor actor=new CurrentActor(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
            "test","Test",Set.of(RoleCode.MEMBER),Set.of(),Set.of());
    private final McpOAuthService oauth=new McpOAuthService(jdbc,new ObjectMapper(),
            new McpProperties(true,"http://localhost",null,null,null,null,null,null,null,null,null,5),actors,audit);

    @Test void explicitInternalConsentIssuesShortTokensAndOnlyInternalGrantHasNullExpiry() {
        when(actors.require()).thenReturn(actor);
        when(jdbc.queryForObject(anyString(),eq(UUID.class),eq(actor.membershipId()))).thenReturn(actor.membershipId());
        when(jdbc.queryForObject(anyString(),eq(Long.class),eq(actor.userId()))).thenReturn(3L);
        var issued=oauth.authorizeWecomAgent(Set.of("yingge.read","yingge.write"));
        assertThat(issued.tokens().expiresIn()).isEqualTo(900);
        assertThat(issued.tokens().scope()).contains("yingge.read","yingge.write");
        assertThat(issued.tokens().toString()).doesNotContain(issued.tokens().accessToken(),issued.tokens().refreshToken());
        verify(jdbc).update(contains("cast(? as jsonb),'ACTIVE',?,null"),any(),eq(McpOAuthService.WECOM_AGENT_CLIENT_ID),
                eq(actor.userId()),eq(actor.membershipId()),eq(actor.organizationId()),anyString(),anyString(),any());
    }
    @Test void adminScopesCannotBeGrantedByAMember() {
        when(actors.require()).thenReturn(actor);
        assertThatThrownBy(()->oauth.authorizeWecomAgent(Set.of("yingge.admin"))).isInstanceOf(OAuthProtocolException.class);
        verifyNoInteractions(jdbc);
    }
}
