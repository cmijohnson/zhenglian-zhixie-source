package com.yingge.cloud.interaction.api;

import com.yingge.cloud.interaction.infrastructure.InteractionEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class InteractionDtos {
    private InteractionDtos() {
    }

    public record CreateRequest(
            @NotNull Instant contactedAt,
            @NotBlank @Size(max = 80) String channel,
            @NotBlank @Size(max = 500) String summary,
            @Size(max = 10000) String details,
            @Size(max = 4000) String outcome,
            @Size(max = 4000) String verificationTodo,
            Instant nextFollowUpAt,
            String currentStatus,
            String intentionLevel,
            String verificationStatus,
            Set<UUID> tagIds
    ) {}

    public record Response(
            UUID id, UUID customerId, Instant contactedAt, String channel, String summary, String details,
            String outcome, String verificationTodo, Instant nextFollowUpAt,
            UUID createdByMembershipId, String createdByDisplayName, Instant createdAt
    ) {
        public static Response from(InteractionEntity row) {
            return from(row, row.getCreatedByDisplayName());
        }

        public static Response from(InteractionEntity row, String createdByDisplayName) {
            return new Response(row.getId(), row.getCustomerId(), row.getContactedAt(), row.getChannel(),
                    row.getSummary(), row.getDetails(), row.getOutcome(), row.getVerificationTodo(),
                    row.getNextFollowUpAt(), row.getCreatedByMembershipId(), createdByDisplayName, row.getCreatedAt());
        }
    }
}
