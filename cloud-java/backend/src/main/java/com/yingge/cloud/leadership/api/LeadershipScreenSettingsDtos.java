package com.yingge.cloud.leadership.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class LeadershipScreenSettingsDtos {
    private LeadershipScreenSettingsDtos() {}

    public record Settings(
            boolean animationsEnabled,
            boolean standbyEnabled,
            int idleTimeoutMinutes,
            int sceneDurationSeconds,
            int eventDurationSeconds,
            String motionIntensity,
            List<String> sceneOrder,
            List<String> enabledScenes,
            List<String> enabledEvents,
            Instant updatedAt,
            long version
    ) {}

    public record UpdateRequest(
            boolean animationsEnabled,
            boolean standbyEnabled,
            @Min(5) @Max(120) int idleTimeoutMinutes,
            @Min(5) @Max(60) int sceneDurationSeconds,
            @Min(3) @Max(15) int eventDurationSeconds,
            @NotBlank String motionIntensity,
            @NotNull @Size(min = 7, max = 7) List<String> sceneOrder,
            @NotEmpty List<String> enabledScenes,
            @NotNull List<String> enabledEvents,
            Long expectedVersion
    ) {}
}
