package com.yingge.cloud.wecom.application;

import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class WecomAgentPolicyService {
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuditService audit;
    private final WecomAgentCipher cipher;

    public WecomAgentPolicyService(JdbcTemplate jdbc, CurrentActorProvider actors,
                                   AuditService audit, WecomAgentCipher cipher) {
        this.jdbc = jdbc; this.actors = actors; this.audit = audit; this.cipher = cipher;
    }

    public record Policy(boolean enabled, String submissionChatId, String broadcastChatId,
                         boolean fullDataRiskAccepted, long version) {}
    public record Update(boolean enabled, String submissionChatId, String broadcastChatId,
                         boolean acceptFullDataRisk, long expectedVersion) {}

    public Policy current(UUID organizationId) {
        return jdbc.query("""
                select enabled,submission_chat_id,broadcast_chat_id,full_data_risk_accepted_at,version
                from wecom_agent_settings where organization_id=?
                """, (rs, i) -> new Policy(rs.getBoolean(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4) != null, rs.getLong(5)), organizationId)
                .stream().findFirst().orElse(new Policy(false, "", "", false, 0));
    }

    public Policy administratorView() { requireAdmin(); return current(actors.require().organizationId()); }

    @Transactional
    public Policy update(Update body) {
        requireAdmin();
        var actor = actors.require();
        String submission = chatId(body.submissionChatId());
        String broadcast = chatId(body.broadcastChatId());
        if (body.enabled() && (submission.isBlank() || broadcast.isBlank() || submission.equals(broadcast))) {
            throw ApiException.badRequest("WECOM_GROUPS_INVALID", "启用前必须配置不同的业务群和广播群 chat_id");
        }
        if (body.enabled() && !body.acceptFullDataRisk()) {
            throw ApiException.badRequest("WECOM_GROUP_RISK_CONFIRMATION_REQUIRED", "请确认业务群完整资料及广播姓名、订单和奖金的公开风险");
        }
        if (body.enabled() && !cipher.configured()) {
            throw ApiException.unavailable("WECOM_ENCRYPTION_UNAVAILABLE", "请先配置 Agent 加密密钥");
        }
        jdbc.update("insert into wecom_agent_settings(organization_id) values (?) on conflict do nothing", actor.organizationId());
        int changed = jdbc.update("""
                update wecom_agent_settings set enabled=?,submission_chat_id=?,broadcast_chat_id=?,
                  full_data_risk_accepted_at=case when ? then now() else null end,
                  version=version+1,updated_at=now()
                where organization_id=? and version=?
                """, body.enabled(), submission, broadcast, body.acceptFullDataRisk(), actor.organizationId(), body.expectedVersion());
        if (changed != 1) throw ApiException.conflict("STALE_VERSION", "配置已更新，请刷新后重试");
        audit.record(actor, "WECOM_AGENT_POLICY_UPDATED", "WECOM_AGENT", actor.organizationId().toString(),
                "SUCCESS", Map.of("enabled", body.enabled(), "riskAccepted", body.acceptFullDataRisk()));
        return current(actor.organizationId());
    }

    public boolean allows(UUID organizationId, String chatId, String chatType) {
        Policy policy = current(organizationId);
        if (!policy.enabled() || !policy.fullDataRiskAccepted()) return false;
        return "single".equals(chatType) || ("group".equals(chatType) && policy.submissionChatId().equals(chatId));
    }

    private String chatId(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > 256 || clean.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("WECOM_CHAT_ID_INVALID", "群 chat_id 格式无效");
        }
        return clean;
    }
    private void requireAdmin() {
        if (!actors.require().isAdmin()) throw ApiException.forbidden("仅组织管理员可以管理企业微信 Agent");
    }
}
