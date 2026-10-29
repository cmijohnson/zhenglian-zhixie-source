package com.yingge.cloud.wecom.api;

import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.shared.api.ApiException;
import com.yingge.cloud.wecom.application.WecomAgentPolicyService;
import com.yingge.cloud.wecom.application.WecomAiBotBindingService;
import com.yingge.cloud.wecom.application.WecomMcpAuthorizationService;
import com.yingge.cloud.wecom.application.WecomPendingActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wecom/agent")
public class WecomAgentController {
    private final CurrentActorProvider actors;
    private final WecomAgentPolicyService policy;
    private final WecomMcpAuthorizationService authorization;
    private final WecomAiBotBindingService bindings;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final WecomPendingActionService pending;

    public WecomAgentController(CurrentActorProvider actors, WecomAgentPolicyService policy,
            WecomMcpAuthorizationService authorization, WecomAiBotBindingService bindings,
            JdbcTemplate jdbc, AuditService audit, WecomPendingActionService pending) {
        this.actors=actors; this.policy=policy; this.authorization=authorization;
        this.bindings=bindings; this.jdbc=jdbc; this.audit=audit; this.pending=pending;
    }

    public record GrantRequest(@NotEmpty Set<String> scopes, boolean consent) {}
    public record RetryRequest(boolean verifiedNotDelivered) {}
    public record Execution(UUID id, String command, String status, String errorCode, Instant createdAt) {}
    public record Delivery(UUID id, String status, int attempts, String errorCode, Instant createdAt) {}
    public record ObservedChat(String chatId, Instant lastSeenAt, long eventCount) {}

    @GetMapping("/authorization")
    public WecomMcpAuthorizationService.AuthorizationView authorization() { return authorization.current(); }

    @PostMapping("/authorization")
    public WecomMcpAuthorizationService.AuthorizationView authorize(@Valid @RequestBody GrantRequest body) {
        if (!body.consent()) throw ApiException.badRequest("WECOM_CONSENT_REQUIRED", "请明确确认授权范围和群内公开风险");
        if (!bindings.current().bound()) throw ApiException.conflict("WECOM_BINDING_REQUIRED", "请先绑定企业微信账号");
        return authorization.authorize(body.scopes());
    }

    @DeleteMapping("/authorization")
    public void revoke() { authorization.revoke(); }

    @GetMapping("/pending-actions")
    public List<WecomPendingActionService.PendingView> pendingActions() { return pending.current(); }

    @PostMapping("/pending-actions/{id}/resume")
    public Map<String,String> resumeAction(@PathVariable UUID id) { return Map.of("message", pending.resume(id)); }

    @GetMapping("/settings")
    public WecomAgentPolicyService.Policy settings() { return policy.administratorView(); }

    @PutMapping("/settings")
    public WecomAgentPolicyService.Policy update(@NotNull @RequestBody WecomAgentPolicyService.Update body) {
        return policy.update(body);
    }

    @GetMapping("/executions")
    public List<Execution> executions() {
        requireAdmin();
        return jdbc.query("""
                select id,command_name,status,error_code,received_at from wecom_ai_bot_event_inbox
                where organization_id=? order by received_at desc limit 100
                """, (rs,i)->new Execution(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getTimestamp(5).toInstant()),actors.require().organizationId());
    }

    @GetMapping("/observed-chats")
    public List<ObservedChat> observedChats() {
        requireAdmin();
        return jdbc.query("""
                select chat_id,max(received_at),count(*) from wecom_ai_bot_event_inbox
                where organization_id=? and chat_type='group' and chat_id is not null
                  and received_at >= now()-interval '7 days'
                group by chat_id order by max(received_at) desc limit 30
                """, (rs,i)->new ObservedChat(rs.getString(1),rs.getTimestamp(2).toInstant(),
                rs.getLong(3)),actors.require().organizationId());
    }

    @GetMapping("/deliveries")
    public List<Delivery> deliveries() {
        requireAdmin();
        return jdbc.query("""
                select id,status,attempts,error_code,created_at from wecom_ai_bot_message_outbox
                where organization_id=? order by created_at desc limit 100
                """, (rs,i)->new Delivery(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),
                rs.getString(4),rs.getTimestamp(5).toInstant()),actors.require().organizationId());
    }

    @PostMapping("/deliveries/{id}/retry")
    @Transactional
    public void retry(@PathVariable UUID id, @RequestBody RetryRequest body) {
        requireAdmin();
        var actor=actors.require();
        int count=jdbc.update("""
                update wecom_ai_bot_message_outbox set status='PENDING',next_attempt_at=now(),
                  lease_until=null,updated_at=now(),error_code=null
                where id=? and organization_id=? and (status='FAILED' or (status='UNCERTAIN' and ?))
                """,id,actor.organizationId(),body.verifiedNotDelivered());
        if(count!=1) throw ApiException.conflict("WECOM_DELIVERY_RETRY_BLOCKED", "仅失败消息可重试；不确定消息必须先核实尚未送达");
        audit.record(actor,"WECOM_MESSAGE_MANUAL_RETRY","WECOM_MESSAGE",id.toString(),"SUCCESS",
                Map.of("verifiedNotDelivered",body.verifiedNotDelivered()));
    }

    private void requireAdmin() { if (!actors.require().isAdmin()) throw ApiException.forbidden("仅组织管理员可以管理 Agent"); }
}
