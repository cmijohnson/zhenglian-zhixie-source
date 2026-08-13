package com.yingge.cloud.interaction.application;

import com.yingge.cloud.audit.application.AuditService;
import com.yingge.cloud.auth.application.CurrentActorProvider;
import com.yingge.cloud.customer.application.CustomerService;
import com.yingge.cloud.customer.domain.CustomerOptions;
import com.yingge.cloud.customer.domain.CustomerQuality;
import com.yingge.cloud.followup.application.CustomerFollowUpService;
import com.yingge.cloud.interaction.api.InteractionDtos;
import com.yingge.cloud.interaction.infrastructure.InteractionEntity;
import com.yingge.cloud.interaction.infrastructure.InteractionRepository;
import com.yingge.cloud.intelligence.application.CustomerIntelligenceQueue;
import com.yingge.cloud.permission.application.PermissionService;
import com.yingge.cloud.reminder.application.ReminderPort;
import com.yingge.cloud.shared.api.ApiException;
import com.yingge.cloud.shared.domain.TextNormalizer;
import com.yingge.cloud.tag.infrastructure.TagEntity;
import com.yingge.cloud.tag.infrastructure.TagRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class InteractionService {
    private final InteractionRepository interactions;
    private final CustomerService customers;
    private final TagRepository tags;
    private final ReminderPort reminders;
    private final CurrentActorProvider actors;
    private final PermissionService permissions;
    private final AuditService audit;
    private final CustomerFollowUpService followUps;
    private final EntityManager entityManager;
    private final CustomerIntelligenceQueue intelligenceQueue;

    public InteractionService(InteractionRepository interactions, CustomerService customers, TagRepository tags,
                              ReminderPort reminders, CurrentActorProvider actors,
                              PermissionService permissions, AuditService audit,
                              CustomerFollowUpService followUps, EntityManager entityManager,
                              CustomerIntelligenceQueue intelligenceQueue) {
        this.interactions = interactions;
        this.customers = customers;
        this.tags = tags;
        this.reminders = reminders;
        this.actors = actors;
        this.permissions = permissions;
        this.audit = audit;
        this.followUps = followUps;
        this.entityManager = entityManager;
        this.intelligenceQueue = intelligenceQueue;
    }

    @Transactional(readOnly = true)
    public List<InteractionDtos.Response> list(UUID customerId) {
        var actor = actors.require();
        customers.requireVisibleEntity(customerId, actor);
        return interactions.findByCustomerIdOrderByContactedAtDesc(customerId).stream()
                .map(InteractionDtos.Response::from).toList();
    }

    @Transactional
    public InteractionDtos.Response create(UUID customerId, InteractionDtos.CreateRequest body) {
        var actor = actors.require();
        permissions.requireCustomerWrite(actor);
        var customer = customers.requireVisibleEntity(customerId, actor);
        customers.requireTimelineEditable(customer, actor);
        validateOptionalChanges(body.currentStatus(), body.intentionLevel(), body.verificationStatus());

        var interaction = interactions.save(new InteractionEntity(
                actor.organizationId(), customerId, body.contactedAt(), body.channel().trim(), body.summary().trim(),
                TextNormalizer.optional(body.details()), TextNormalizer.optional(body.outcome()),
                TextNormalizer.optional(body.verificationTodo()), body.nextFollowUpAt(), actor.membershipId()
        ));
        customer.markContact(body.contactedAt(), body.nextFollowUpAt(), TextNormalizer.optional(body.currentStatus()),
                TextNormalizer.optional(body.intentionLevel()), TextNormalizer.optional(body.verificationStatus()));
        if (body.tagIds() != null) {
            Set<TagEntity> replacement = new LinkedHashSet<>(
                    tags.findByOrganizationIdAndActiveTrueAndIdIn(actor.organizationId(), body.tagIds())
            );
            if (replacement.size() != body.tagIds().size()) {
                throw ApiException.badRequest("TAG_NOT_FOUND", "包含不存在或不属于当前组织的标签");
            }
            if (CustomerQuality.hasMultipleQualityTags(
                    replacement.stream().map(TagEntity::getCategory).toList())) {
                throw ApiException.badRequest("CUSTOMER_QUALITY_CONFLICT",
                        "客户质量只能选择 A 类、B 类、C 类中的一项");
            }
            customer.replaceTags(replacement);
        }
        reminders.replacePending(customerId, interaction.getId(), customer.getOwnerMembershipId(),
                body.nextFollowUpAt(), "跟进 " + customer.getFullName(), actor);
        // Some callers persist their own record with JDBC immediately after this call and
        // reference both the interaction and its reminder. Flush the complete timeline
        // transaction so those foreign keys and reassignment updates observe the new rows.
        entityManager.flush();
        audit.record(actor, "INTERACTION_CREATED", "INTERACTION", interaction.getId().toString(), "SUCCESS",
                java.util.Map.of("customerId", customerId.toString()));
        followUps.completeStaleReminders(actor.organizationId(), customerId);
        followUps.reconcileCustomer(actor.organizationId(), customerId);
        intelligenceQueue.enqueue(actor.organizationId(), customerId,
                CustomerIntelligenceQueue.TriggerType.TIMELINE_UPDATED, actor.membershipId());
        return InteractionDtos.Response.from(interaction, actor.displayName());
    }

    private void validateOptionalChanges(String status, String intention, String verification) {
        String normalizedStatus = TextNormalizer.optional(status);
        String normalizedIntention = TextNormalizer.optional(intention);
        String normalizedVerification = TextNormalizer.optional(verification);
        if (normalizedStatus != null && !CustomerOptions.STATUSES.contains(normalizedStatus)) {
            throw ApiException.badRequest("INVALID_CURRENT_STATUS", "当前状态无效");
        }
        if (normalizedIntention != null && !CustomerOptions.LEVELS.contains(normalizedIntention)) {
            throw ApiException.badRequest("INVALID_INTENTION_LEVEL", "报名意向无效");
        }
        if (normalizedVerification != null && !CustomerOptions.VERIFICATION_STATUSES.contains(normalizedVerification)) {
            throw ApiException.badRequest("INVALID_VERIFICATION_STATUS", "核实状态无效");
        }
    }
}
