package com.yingge.cloud.wecom.application;

import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.auth.infrastructure.CloudPrincipal;
import com.yingge.cloud.shared.api.ApiException;
import com.yingge.cloud.wecom.api.WecomAiBotDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WecomAiBotBindingService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuthenticationManager authenticationManager;
    private final WecomAiBotSettings settings;

    public WecomAiBotBindingService(JdbcTemplate jdbc, CurrentActorProvider actors,
                                    AuthenticationManager authenticationManager, WecomAiBotSettings settings) {
        this.jdbc = jdbc;
        this.actors = actors;
        this.authenticationManager = authenticationManager;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public WecomAiBotDtos.BindingResponse current() {
        var actor = actors.require();
        Binding row = findByMembership(actor.organizationId(), actor.membershipId());
        return row == null
                ? new WecomAiBotDtos.BindingResponse(settings.configured(), false, null, null, null, null)
                : new WecomAiBotDtos.BindingResponse(settings.configured(), true, row.id(), mask(row.wecomUserId()),
                row.boundAt(), row.lastUsedAt());
    }

    @Transactional
    public WecomAiBotDtos.BindingCodeResponse issueCode() {
        if (!settings.configured()) {
            throw ApiException.conflict("WECOM_AIBOT_NOT_CONFIGURED", "企业微信机器人尚未完成服务端配置");
        }
        var actor = actors.require();
        String code = randomCode();
        Instant expiresAt = Instant.now().plus(CODE_TTL);
        jdbc.update("""
                update wecom_ai_bot_binding_codes set consumed_at=now()
                where organization_id=? and membership_id=? and consumed_at is null
                """, actor.organizationId(), actor.membershipId());
        jdbc.update("""
                insert into wecom_ai_bot_binding_codes
                  (id,organization_id,membership_id,user_account_id,code_hash,expires_at)
                values (?,?,?,?,?,?)
                """, UUID.randomUUID(), actor.organizationId(), actor.membershipId(), actor.userId(),
                sha256(code), Timestamp.from(expiresAt));
        return new WecomAiBotDtos.BindingCodeResponse(code, expiresAt);
    }

    @Transactional
    public String consumeCode(UUID organizationId, String wecomUserId, String suppliedCode) {
        if (organizationId == null || wecomUserId == null || wecomUserId.isBlank()) {
            throw ApiException.badRequest("WECOM_BINDING_IDENTITY_INVALID", "企业微信身份不可用");
        }
        String normalized = normalizeCode(suppliedCode);
        List<CodeRow> matches = jdbc.query("""
                select code.id,code.membership_id,code.user_account_id,account.display_name
                from wecom_ai_bot_binding_codes code
                join memberships membership on membership.id=code.membership_id
                  and membership.organization_id=code.organization_id and membership.status='ACTIVE'
                join user_accounts account on account.id=code.user_account_id and account.enabled=true
                where code.organization_id=? and code.code_hash=? and code.consumed_at is null
                  and code.expires_at>now()
                for update
                """, (rs, index) -> new CodeRow(rs.getObject("id", UUID.class),
                rs.getObject("membership_id", UUID.class), rs.getObject("user_account_id", UUID.class),
                rs.getString("display_name")), organizationId, sha256(normalized));
        if (matches.isEmpty()) {
            throw ApiException.badRequest("WECOM_BINDING_CODE_INVALID", "绑定码无效或已过期，请在英格云设置中重新生成");
        }
        CodeRow code = matches.getFirst();
        // Rebinding changes the platform identity: the new identity must consent afresh.
        for (String table : List.of("wecom_agent_conversations", "wecom_deal_conversations")) {
            jdbc.update("delete from " + table + " " + """
                    where organization_id=? and (membership_id=? or membership_id in
                      (select membership_id from wecom_ai_bot_identity_bindings
                        where organization_id=? and wecom_userid=? and active=true))
                    """, organizationId, code.membershipId(), organizationId, wecomUserId);
        }
        jdbc.update("""
                update oauth_grants set status='REVOKED',revoked_at=now()
                where id in (select credential.grant_id from wecom_agent_credentials credential
                  where credential.organization_id=? and (credential.membership_id=? or credential.membership_id in
                    (select membership_id from wecom_ai_bot_identity_bindings
                      where organization_id=? and wecom_userid=? and active=true)))
                """, organizationId, code.membershipId(), organizationId, wecomUserId);
        jdbc.update("""
                delete from wecom_agent_credentials where organization_id=? and (membership_id=? or membership_id in
                  (select membership_id from wecom_ai_bot_identity_bindings where organization_id=? and wecom_userid=? and active=true))
                """, organizationId, code.membershipId(), organizationId, wecomUserId);
        jdbc.update("""
                update wecom_ai_bot_identity_bindings
                set active=false,revoked_at=now(),updated_at=now()
                where organization_id=? and active=true and (membership_id=? or wecom_userid=?)
                """, organizationId, code.membershipId(), wecomUserId);
        jdbc.update("""
                insert into wecom_ai_bot_identity_bindings
                  (id,organization_id,membership_id,user_account_id,wecom_userid)
                values (?,?,?,?,?)
                """, UUID.randomUUID(), organizationId, code.membershipId(), code.userAccountId(), wecomUserId);
        jdbc.update("update wecom_ai_bot_binding_codes set consumed_at=now() where id=?", code.id());
        return code.displayName();
    }

    @Transactional(readOnly = true)
    public UUID activeMembership(UUID organizationId, String wecomUserId) {
        return jdbc.query("""
                select binding.membership_id
                from wecom_ai_bot_identity_bindings binding
                join memberships membership on membership.id=binding.membership_id
                  and membership.organization_id=binding.organization_id and membership.status='ACTIVE'
                join user_accounts account on account.id=binding.user_account_id and account.enabled=true
                where binding.organization_id=? and binding.wecom_userid=? and binding.active=true
                """, (rs, index) -> rs.getObject(1, UUID.class), organizationId, wecomUserId)
                .stream().findFirst().orElse(null);
    }

    @Transactional
    public void touch(UUID organizationId, String wecomUserId) {
        jdbc.update("""
                update wecom_ai_bot_identity_bindings set last_used_at=now(),updated_at=now()
                where organization_id=? and wecom_userid=? and active=true
                """, organizationId, wecomUserId);
    }

    @Transactional
    public void unbind(String currentPassword) {
        CloudPrincipal principal = actors.requirePrincipal();
        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    principal.getUsername(), currentPassword));
        } catch (RuntimeException exception) {
            throw ApiException.badRequest("CURRENT_PASSWORD_INVALID", "当前密码不正确");
        }
        jdbc.update("""
                update oauth_grants set status='REVOKED',revoked_at=now()
                where id in (select grant_id from wecom_agent_credentials where organization_id=? and membership_id=?)
                """, principal.actor().organizationId(), principal.actor().membershipId());
        jdbc.update("delete from wecom_agent_credentials where organization_id=? and membership_id=?",
                principal.actor().organizationId(), principal.actor().membershipId());
        jdbc.update("delete from wecom_agent_conversations where organization_id=? and membership_id=?",
                principal.actor().organizationId(), principal.actor().membershipId());
        jdbc.update("delete from wecom_deal_conversations where organization_id=? and membership_id=?",
                principal.actor().organizationId(), principal.actor().membershipId());
        jdbc.update("""
                update wecom_ai_bot_identity_bindings
                set active=false,revoked_at=now(),updated_at=now()
                where organization_id=? and membership_id=? and active=true
                """, principal.actor().organizationId(), principal.actor().membershipId());
    }

    private Binding findByMembership(UUID organizationId, UUID membershipId) {
        return jdbc.query("""
                select id,wecom_userid,bound_at,last_used_at
                from wecom_ai_bot_identity_bindings
                where organization_id=? and membership_id=? and active=true
                """, (rs, index) -> new Binding(rs.getObject("id", UUID.class), rs.getString("wecom_userid"),
                rs.getTimestamp("bound_at").toInstant(), instant(rs.getTimestamp("last_used_at"))),
                organizationId, membershipId).stream().findFirst().orElse(null);
    }

    static String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private String randomCode() {
        StringBuilder text = new StringBuilder("YG-");
        for (int i = 0; i < 8; i++) text.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return text.toString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("WECOM_BINDING_HASH_FAILED", exception);
        }
    }

    private String mask(String value) {
        if (value == null || value.length() <= 6) return "***";
        return value.substring(0, 3) + "…" + value.substring(value.length() - 3);
    }

    private Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private record Binding(UUID id, String wecomUserId, Instant boundAt, Instant lastUsedAt) {}
    private record CodeRow(UUID id, UUID membershipId, UUID userAccountId, String displayName) {}
}
