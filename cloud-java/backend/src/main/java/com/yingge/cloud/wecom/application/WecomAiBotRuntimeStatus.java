package com.yingge.cloud.wecom.application;

import com.yingge.cloud.wecom.api.WecomAiBotDtos;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class WecomAiBotRuntimeStatus {
    private final WecomAiBotSettings settings;
    private final AtomicReference<String> state = new AtomicReference<>("DISABLED");
    private final AtomicReference<Instant> connectedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastMessageAt = new AtomicReference<>();
    private final AtomicReference<String> lastErrorCode = new AtomicReference<>();

    public WecomAiBotRuntimeStatus(WecomAiBotSettings settings) {
        this.settings = settings;
        if (settings.enabled()) state.set(settings.configured() ? "DISCONNECTED" : "MISCONFIGURED");
    }

    public void connecting() { state.set("CONNECTING"); }
    public void connected() {
        state.set("CONNECTED");
        connectedAt.set(Instant.now());
        lastErrorCode.set(null);
    }
    public void messageReceived() { lastMessageAt.set(Instant.now()); }
    public boolean isConnected() { return "CONNECTED".equals(state.get()); }
    public void error(String code) { lastErrorCode.set(code); }
    public void unavailable(String stateName, String code) {
        state.set(stateName);
        lastErrorCode.set(code);
    }
    public void disconnected(String code) {
        state.set(settings.configured() ? "DISCONNECTED" : "MISCONFIGURED");
        lastErrorCode.set(code);
    }

    public WecomAiBotDtos.StatusResponse response() {
        return new WecomAiBotDtos.StatusResponse(settings.enabled(), settings.configured(), state.get(),
                connectedAt.get(), lastMessageAt.get(), lastErrorCode.get());
    }
}
