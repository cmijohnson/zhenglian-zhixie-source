package com.yingge.cloud.wecom.api;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public final class WecomAiBotDtos {
    private WecomAiBotDtos() {}

    public record BindingResponse(boolean botAvailable, boolean bound, UUID bindingId,
                                  String maskedWecomUserId, Instant boundAt, Instant lastUsedAt) {}

    public record BindingCodeResponse(String code, Instant expiresAt) {}

    public record UnbindRequest(@NotBlank String currentPassword) {}

    public record StatusResponse(boolean enabled, boolean configured, String state,
                                 Instant connectedAt, Instant lastMessageAt, String lastErrorCode) {}
}
