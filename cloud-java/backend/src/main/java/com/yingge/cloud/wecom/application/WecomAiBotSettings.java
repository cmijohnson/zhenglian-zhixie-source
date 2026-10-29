package com.yingge.cloud.wecom.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
public class WecomAiBotSettings {
    private final boolean enabled;
    private final String endpoint;
    private final String botId;
    private final String secret;
    private final String organizationId;
    private final String publicBaseUrl;

    public WecomAiBotSettings(
            @Value("${app.wecom.aibot.enabled:false}") boolean enabled,
            @Value("${app.wecom.aibot.endpoint:wss://openws.work.weixin.qq.com}") String endpoint,
            @Value("${app.wecom.aibot.bot-id:}") String botId,
            @Value("${app.wecom.aibot.secret:}") String secret,
            @Value("${app.wecom.aibot.organization-id:}") String organizationId,
            @Value("${app.wecom.aibot.public-base-url:}") String publicBaseUrl) {
        this.enabled = enabled;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.botId = botId == null ? "" : botId.trim();
        this.secret = secret == null ? "" : secret.trim();
        this.organizationId = organizationId == null ? "" : organizationId.trim();
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    public boolean enabled() { return enabled; }
    public URI endpoint() { return URI.create(endpoint); }
    public String botId() { return botId; }
    public String secret() { return secret; }
    public String publicBaseUrl() { return publicBaseUrl; }

    public UUID organizationId() {
        return organizationId.isBlank() ? null : UUID.fromString(organizationId);
    }

    public boolean configured() {
        if (!enabled || botId.isBlank() || secret.isBlank()) return false;
        try {
            return organizationId() != null
                    && "wss".equalsIgnoreCase(endpoint().getScheme())
                    && "openws.work.weixin.qq.com".equalsIgnoreCase(endpoint().getHost())
                    && endpoint().getUserInfo() == null && endpoint().getQuery() == null
                    && endpoint().getFragment() == null
                    && (endpoint().getPort() == -1 || endpoint().getPort() == 443)
                    && (endpoint().getPath().isEmpty() || "/".equals(endpoint().getPath()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.trim();
        return clean.endsWith("/") ? clean.substring(0, clean.length() - 1) : clean;
    }
}
