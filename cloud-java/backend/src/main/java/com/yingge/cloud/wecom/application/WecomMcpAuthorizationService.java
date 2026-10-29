package com.yingge.cloud.wecom.application;

import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.mcp.McpProperties;
import com.yingge.cloud.mcp.oauth.McpOAuthService;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side credentials for an explicitly consented, first-party MCP client. */
@Service
public class WecomMcpAuthorizationService {
    private final JdbcTemplate jdbc;
    private final McpOAuthService oauth;
    private final CurrentActorProvider actors;
    private final WecomActorExecutor executor;
    private final WecomAgentCipher cipher;
    private final McpProperties properties;
    private final TransactionTemplate transactions;

    public WecomMcpAuthorizationService(JdbcTemplate jdbc, McpOAuthService oauth, CurrentActorProvider actors,
            WecomActorExecutor executor, WecomAgentCipher cipher, McpProperties properties,
            PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.oauth=oauth; this.actors=actors; this.executor=executor; this.cipher=cipher;
        this.properties=properties; this.transactions=new TransactionTemplate(manager);
    }

    public AuthorizationView current() {
        var actor=actors.require();
        return jdbc.query("""
                select g.id, g.scopes, g.status, g.expires_at,
                    (u.enabled and m.status='ACTIVE' and o.status='ACTIVE' and
                     (g.expires_at is null or g.expires_at>now())) identity_active from oauth_grants g
                join wecom_agent_credentials c on c.grant_id=g.id
                join memberships m on m.id=g.membership_id
                join user_accounts u on u.id=g.user_id
                join organizations o on o.id=g.organization_id
                where c.membership_id=? and c.organization_id=?
                """, (r,i) -> new AuthorizationView(r.getObject("id",UUID.class),
                scopes(r.getString("scopes")), "ACTIVE".equals(r.getString("status"))&&r.getBoolean("identity_active"),
                r.getTimestamp("expires_at")==null, instant(r.getTimestamp("expires_at"))),
                actor.membershipId(),actor.organizationId()).stream().findFirst()
                .orElse(new AuthorizationView(null,Set.of(),false,false,null));
    }

    public AuthorizationView authorize(Set<String> scopes) {
        var actor=actors.require();
        if (!cipher.configured()) throw ApiException.unavailable("WECOM_AGENT_ENCRYPTION_UNAVAILABLE","服务器尚未配置 Agent 加密密钥");
        return transactions.execute(status -> {
            var grant=oauth.authorizeWecomAgent(scopes);
            persist(actor.organizationId(),actor.membershipId(),grant.grantId(),grant.tokens());
            return current();
        });
    }

    public void revoke() {
        var actor=actors.require();
        transactions.executeWithoutResult(status -> {
            AuthorizationView view=current();
            if (view.active()) oauth.revokeGrant(view.grantId(),false);
            jdbc.update("delete from wecom_agent_credentials where membership_id=? and organization_id=?",
                    actor.membershipId(),actor.organizationId());
            jdbc.update("delete from wecom_agent_conversations where membership_id=? and organization_id=?",
                    actor.membershipId(),actor.organizationId());
        });
    }

    public McpOAuthService.TokenAuthentication requireAuthorization(UUID org, UUID membership) {
        return oauth.authenticate(accessToken(org,membership));
    }

    public void requireScope(UUID org,UUID membership,String scope) {
        var actor=actors.require();
        if (!actor.organizationId().equals(org)||!actor.membershipId().equals(membership)) throw denied();
        var token=requireAuthorization(org,membership);
        boolean roleAllowed=switch(scope) {
            case "yingge.admin", "yingge.admin.read" -> actor.isAdmin();
            case "yingge.write" -> actor.canWriteCustomers();
            case "yingge.ai" -> actor.canWriteCustomers();
            case "yingge.read" -> true;
            default -> false;
        };
        boolean scopeGranted=token.scopes().contains(scope)||("yingge.admin.read".equals(scope)&&token.scopes().contains("yingge.admin"));
        if(!scopeGranted||!roleAllowed) throw new ApiException(HttpStatus.FORBIDDEN,"WECOM_AGENT_SCOPE_REQUIRED","当前账号未授权此操作");
    }

    public String accessToken(UUID org,UUID membership) {
        return transactions.execute(status -> {
            var rows=jdbc.query("""
                    select grant_id,encrypted_access_token,encrypted_refresh_token,access_expires_at
                    from wecom_agent_credentials where organization_id=? and membership_id=? for update
                    """, (r,i)->new Credential(r.getObject("grant_id",UUID.class),r.getString("encrypted_access_token"),
                    r.getString("encrypted_refresh_token"),r.getTimestamp("access_expires_at").toInstant()),org,membership);
            if(rows.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN,"WECOM_AGENT_AUTHORIZATION_REQUIRED","请先在英格云授权企业微信 Agent");
            Credential row=rows.getFirst();
            String access;
            // A full Agent turn may take 120 seconds; do not start it with a nearly expired bearer.
            if(!row.expiresAt().isAfter(Instant.now().plusSeconds(180))) {
                var tokens=oauth.token(Map.of("grant_type","refresh_token","client_id",McpOAuthService.WECOM_AGENT_CLIENT_ID,
                        "refresh_token",cipher.decrypt(row.refresh())));
                persist(org,membership,row.grantId(),tokens);
                access=tokens.accessToken();
            } else access=cipher.decrypt(row.access());
            var token=oauth.authenticate(access);
            if(!token.organizationId().equals(org)||!token.membershipId().equals(membership)
                    ||!token.clientId().equals(McpOAuthService.WECOM_AGENT_CLIENT_ID)) throw denied();
            return access;
        });
    }

    private void persist(UUID org,UUID member,UUID grant,McpOAuthService.TokenResponse tokens) {
        Instant now=Instant.now();
        jdbc.update("""
                insert into wecom_agent_credentials(organization_id,membership_id,grant_id,encrypted_access_token,
                    encrypted_refresh_token,access_expires_at,refresh_expires_at,updated_at)
                values(?,?,?,?,?,?,?,?) on conflict(membership_id) do update set grant_id=excluded.grant_id,
                    encrypted_access_token=excluded.encrypted_access_token,encrypted_refresh_token=excluded.encrypted_refresh_token,
                    access_expires_at=excluded.access_expires_at,refresh_expires_at=excluded.refresh_expires_at,updated_at=excluded.updated_at
                """,org,member,grant,cipher.encrypt(tokens.accessToken()),cipher.encrypt(tokens.refreshToken()),
                Timestamp.from(now.plusSeconds(tokens.expiresIn())),Timestamp.from(now.plus(java.time.Duration.ofDays(30))),Timestamp.from(now));
    }

    @Scheduled(fixedDelay=3_600_000)
    public void maintain() {
        if(!cipher.configured()) return;
        var members=jdbc.query("""
                select c.organization_id,c.membership_id from wecom_agent_credentials c
                join oauth_grants g on g.id=c.grant_id and g.status='ACTIVE'
                where c.access_expires_at<now()+interval '2 minutes'
                """,(r,i)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class)});
        for(var member:members) {
            try { executor.as(member[1],()->accessToken(member[0],member[1])); }
            catch(RuntimeException ignored) { /* Revoked/disabled grants must never be silently re-issued. */ }
        }
        jdbc.update("delete from wecom_agent_conversations where expires_at<now()");
    }

    private Set<String> scopes(String json) {
        try { return Set.copyOf(new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){})); }
        catch(Exception e) { throw new IllegalStateException("Invalid stored scope metadata"); }
    }
    private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
    private static ApiException denied(){return new ApiException(HttpStatus.FORBIDDEN,"WECOM_AGENT_IDENTITY_MISMATCH","Agent 授权身份不匹配");}
    public record AuthorizationView(UUID grantId,Set<String> scopes,boolean active,boolean longLived,Instant expiresAt){}
    private record Credential(UUID grantId,String access,String refresh,Instant expiresAt) {
        @Override public String toString(){return "Credential[redacted]";}
    }
}
