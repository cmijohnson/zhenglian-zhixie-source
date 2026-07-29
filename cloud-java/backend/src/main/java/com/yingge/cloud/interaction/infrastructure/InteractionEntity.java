package com.yingge.cloud.interaction.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interactions")
public class InteractionEntity {
    @Id
    private UUID id;
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "contacted_at", nullable = false)
    private Instant contactedAt;
    @Column(nullable = false)
    private String channel;
    @Column(nullable = false)
    private String summary;
    private String details;
    private String outcome;
    @Column(name = "verification_todo")
    private String verificationTodo;
    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;
    @Column(name = "created_by_membership_id", nullable = false)
    private UUID createdByMembershipId;
    @Formula("(select account.display_name from memberships member join user_accounts account on account.id=member.user_id where member.id=created_by_membership_id)")
    private String createdByDisplayName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InteractionEntity() {
    }

    public InteractionEntity(UUID organizationId, UUID customerId, Instant contactedAt, String channel,
                             String summary, String details, String outcome, String verificationTodo,
                             Instant nextFollowUpAt, UUID createdByMembershipId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.customerId = customerId;
        this.contactedAt = contactedAt;
        this.channel = channel;
        this.summary = summary;
        this.details = details;
        this.outcome = outcome;
        this.verificationTodo = verificationTodo;
        this.nextFollowUpAt = nextFollowUpAt;
        this.createdByMembershipId = createdByMembershipId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public Instant getContactedAt() { return contactedAt; }
    public String getChannel() { return channel; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public String getOutcome() { return outcome; }
    public String getVerificationTodo() { return verificationTodo; }
    public Instant getNextFollowUpAt() { return nextFollowUpAt; }
    public UUID getCreatedByMembershipId() { return createdByMembershipId; }
    public String getCreatedByDisplayName() { return createdByDisplayName; }
    public Instant getCreatedAt() { return createdAt; }
}
