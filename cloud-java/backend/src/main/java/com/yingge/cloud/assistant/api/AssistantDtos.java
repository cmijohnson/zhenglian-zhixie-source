package com.yingge.cloud.assistant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AssistantDtos {
    private AssistantDtos() {}

    public record ParseRequest(@NotBlank @Size(max = 20000) String text) {}
    public record CustomerDraft(
            String fullName, String nickname, String phone, String wechatId, String qqId,
            String school, String major, String grade, String city, String identityType,
            List<String> focusDirections, String demandSummary, String intentionLevel,
            String urgencyLevel, String verificationStatus, String sourceChannel,
            String currentStatus, String ownerNotes, Instant nextFollowUpAt,
            List<String> uncertainties
    ) {}
    public record InteractionDraft(
            Instant contactedAt, String channel, String summary, String details, String outcome,
            String verificationTodo, Instant nextFollowUpAt, String currentStatus,
            String intentionLevel, String verificationStatus, List<String> uncertainties
    ) {}
    public record Availability(boolean available, String provider, String model) {}
}

