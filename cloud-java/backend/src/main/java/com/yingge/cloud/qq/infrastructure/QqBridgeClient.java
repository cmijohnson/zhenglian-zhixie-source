package com.yingge.cloud.qq.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.yingge.cloud.qq.application.QqProperties;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class QqBridgeClient {
    private final QqProperties properties;
    private final RestClient client;

    @Autowired
    public QqBridgeClient(QqProperties properties, RestClient.Builder builder) {
        this(properties, builder, true);
    }

    QqBridgeClient(QqProperties properties, RestClient.Builder builder, boolean applyTimeout) {
        this.properties = properties;
        if (applyTimeout) {
            Duration timeout = Duration.ofSeconds(40);
            var requestFactory = new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
            requestFactory.setReadTimeout(timeout);
            builder.requestFactory(requestFactory);
        }
        this.client = properties.bridgeBaseUrl() == null || properties.bridgeBaseUrl().isBlank()
                ? null
                : builder.baseUrl(properties.bridgeBaseUrl().replaceAll("/+$", ""))
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.bridgeToken())
                    .build();
    }

    public boolean configured() {
        return client != null && properties.bridgeToken() != null && !properties.bridgeToken().isBlank();
    }

    public JsonNode create(String key) { return post("/v1/instances", Map.of("key", key)); }
    public JsonNode status(String instanceId) { return get("/v1/instances/" + instanceId); }
    public void revoke(String instanceId) {
        requireClient().delete().uri("/v1/instances/{id}", instanceId).retrieve().toBodilessEntity();
    }
    public JsonNode friends(String instanceId, int page, int limit) {
        return get("/v1/instances/" + instanceId + "/friends?page=" + page + "&limit=" + limit);
    }
    public JsonNode messages(String instanceId, String peerUid, long startTime, long endTime, int page, int limit) {
        return post("/v1/instances/" + instanceId + "/messages", Map.of(
                "peerUid", peerUid, "startTime", startTime, "endTime", endTime, "page", page, "limit", limit));
    }

    private JsonNode get(String path) {
        try { return requireClient().get().uri(path).retrieve().body(JsonNode.class); }
        catch (ApiException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(); }
    }

    private JsonNode post(String path, Object body) {
        try {
            return requireClient().post().uri(path).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
        } catch (ApiException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(); }
    }

    private RestClient requireClient() {
        if (!configured()) throw ApiException.unavailable("QQ_BRIDGE_UNAVAILABLE", "QQ Bridge 尚未配置或不可用");
        return client;
    }

    private ApiException unavailable() {
        return ApiException.unavailable("QQ_BRIDGE_UNAVAILABLE", "QQ Bridge 暂时不可用，请稍后重试");
    }
}
