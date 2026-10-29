package com.yingge.cloud.wecom.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yingge.cloud.wecom.application.*;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Wire baseline: @wecom/aibot-node-sdk 1.0.6, commit 80615b987ef69c6028ad764924609247c0725955. */
@Component
@ConditionalOnProperty(name = "app.wecom.aibot.enabled", havingValue = "true")
public class WecomAiBotConnectionManager {
    private static final Pattern CONFIRM_MARKER = Pattern.compile("(?:^|\\n)\\[\\[WECOM_CONFIRM:([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})\\]\\]\\s*$");
    private final WecomAiBotSettings settings;
    private final WecomAiBotRuntimeStatus status;
    private final WecomAiBotCommandService commands;
    private final WecomConnectionLease lease;
    private final WecomMessageOutbox outbox;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
    private final WecomAckQueue acks = new WecomAckQueue(timers, Duration.ofSeconds(5));
    private final ThreadPoolExecutor[] stripes = java.util.stream.IntStream.range(0, 8)
            .mapToObj(i -> new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<Runnable>(32), Thread.ofPlatform().name("wecom-worker-" + i + "-", 0).factory(),
                    new ThreadPoolExecutor.AbortPolicy())).toArray(ThreadPoolExecutor[]::new);
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicReference<String> subscribeId = new AtomicReference<>();
    private final AtomicReference<String> heartbeatId = new AtomicReference<>();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean displaced = new AtomicBoolean();
    private final AtomicInteger missedHeartbeats = new AtomicInteger();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicInteger authenticationFailures = new AtomicInteger();
    private volatile Instant retryAt = Instant.EPOCH;
    private volatile Instant subscribeSentAt = Instant.EPOCH;

    public WecomAiBotConnectionManager(WecomAiBotSettings settings, WecomAiBotRuntimeStatus status,
            WecomAiBotCommandService commands, ObjectMapper json, WecomConnectionLease lease, WecomMessageOutbox outbox) {
        this.settings = settings; this.status = status; this.commands = commands;
        this.json = json; this.lease = lease; this.outbox = outbox;
    }

    @Scheduled(initialDelayString = "${app.wecom.aibot.connect-initial-delay-ms:3000}", fixedDelay = 10000)
    public void maintainConnection() {
        if (stopped.get() || displaced.get() || authenticationFailures.get() >= 5 || !settings.configured()) return;
        try {
            if (!lease.renew(settings.botId())) {
                WebSocket current = socket.get();
                if (current != null) disconnect(current, "WECOM_CONNECTION_LEASE_LOST");
                status.unavailable("STANDBY", "WECOM_CONNECTION_LEASE_HELD"); return;
            }
        } catch (RuntimeException failure) {
            WebSocket current = socket.get();
            if (current != null) disconnect(current, "WECOM_CONNECTION_LEASE_FAILED"); return;
        }
        WebSocket current = socket.get();
        if (current != null) {
            if (!status.isConnected() && subscribeSentAt.plusSeconds(15).isBefore(Instant.now()))
                disconnect(current, "WECOM_SUBSCRIBE_TIMEOUT");
            return;
        }
        if (Instant.now().isBefore(retryAt) || !connecting.compareAndSet(false, true)) return;
        status.connecting();
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(settings.endpoint(), new Listener())
                .whenComplete((connected, failure) -> {
                    connecting.set(false);
                    if (failure != null) { status.disconnected("WECOM_AIBOT_CONNECT_FAILED"); backoff(); }
                });
    }

    @Scheduled(initialDelayString = "${app.wecom.aibot.heartbeat-ms:30000}",
            fixedDelayString = "${app.wecom.aibot.heartbeat-ms:30000}")
    public void heartbeat() {
        WebSocket current = socket.get();
        if (current == null || !status.isConnected()) return;
        if (missedHeartbeats.get() >= 2) { disconnect(current, "WECOM_HEARTBEAT_TIMEOUT"); return; }
        missedHeartbeats.incrementAndGet();
        String id = "ping-" + UUID.randomUUID(); heartbeatId.set(id);
        sendRaw(current, Map.of("cmd", "ping", "headers", Map.of("req_id", id)));
    }

    public boolean ready() { return socket.get() != null && status.isConnected() && !displaced.get(); }
    public CompletableFuture<Void> sendActive(String id, String chatId, String text) {
        return sendAcknowledged(activeFrame(id, chatId, text));
    }
    public CompletableFuture<Void> sendReply(String id, String messageId, String text) {
        return sendAcknowledged(streamReplyFrame(id, messageId, text));
    }
    public CompletableFuture<Void> sendStream(String id, String messageId, String text, boolean finished) {
        return sendAcknowledged(streamFrame(id, messageId, text, finished));
    }
    private CompletableFuture<Void> sendAcknowledged(Map<String, Object> frame) {
        WebSocket current = socket.get();
        if (current == null || !status.isConnected())
            return CompletableFuture.failedFuture(new IllegalStateException("WECOM_NOT_CONNECTED"));
        @SuppressWarnings("unchecked") String id = ((Map<String, String>) frame.get("headers")).get("req_id");
        return acks.enqueue(id, () -> {
            if (socket.get() != current) throw new IllegalStateException("WECOM_CONNECTION_CHANGED");
            sendRaw(current, frame);
        }).whenComplete((ignored, failure) -> {
            if (failure != null && "WECOM_ACK_UNCERTAIN".equals(failure.getMessage()))
                disconnect(current, "WECOM_ACK_UNCERTAIN");
        });
    }

    static Map<String, Object> subscribeFrame(String id, String botId, String secret) {
        return Map.of("cmd", "aibot_subscribe", "headers", Map.of("req_id", id),
                "body", Map.of("bot_id", botId, "secret", secret));
    }
    static Map<String, Object> streamReplyFrame(String id, String messageId, String content) {
        return streamFrame(id,messageId,content,true);
    }
    static Map<String, Object> streamFrame(String id, String messageId, String content, boolean finished) {
        // Only the trusted final service reply has a marker. Incoming text is never passed to this parser.
        var marker = CONFIRM_MARKER.matcher(content == null ? "" : content);
        if (finished && marker.find()) {
            String token=marker.group(1);
            String visible=content.substring(0,marker.start()).stripTrailing();
            return Map.of("cmd","aibot_respond_msg","headers",Map.of("req_id",id),"body",Map.of(
                    "msgtype","stream_with_template_card",
                    "stream",Map.of("id","yingge-"+safeId(messageId),"finish",true,"content",limitUtf8(visible)),
                    "template_card",Map.of("card_type","button_interaction","task_id",token,
                            "main_title",Map.of("title","请核对以上内容","desc","仅发起人可以确认，修改后原卡片失效"),
                            "sub_title_text","确认即执行业务操作；如需修改，请继续发送指令。",
                            "card_action",Map.of("type",0),
                            "button_list",List.of(Map.of("text","确认","style",1,"key","confirm:"+token)))));
        }
        return Map.of("cmd", "aibot_respond_msg", "headers", Map.of("req_id", id),
                "body", Map.of("msgtype", "stream", "stream", Map.of(
                        "id", "yingge-" + safeId(messageId), "finish", finished, "content", limitUtf8(content))));
    }
    static Map<String, Object> activeFrame(String id, String chatId, String content) {
        String wireChatId=chatId!=null&&chatId.startsWith("single:")?chatId.substring("single:".length()):chatId;
        return Map.of("cmd", "aibot_send_msg", "headers", Map.of("req_id", id),
                "body", Map.of("chatid", wireChatId, "msgtype", "markdown", "markdown", Map.of("content", limitUtf8(content))));
    }
    static Map<String,Object> cardProgressFrame(String requestId,String taskId,String userId) {
        // Official UpdateTemplateCardBody: response_type, template_card, optional userids.
        return Map.of("cmd","aibot_respond_update_msg","headers",Map.of("req_id",requestId),
                "body",Map.of("response_type","update_template_card","userids",List.of(userId),
                        "template_card",Map.of("card_type","text_notice","task_id",taskId,
                                "main_title",Map.of("title","正在核对","desc","请以随后发送的业务结果为准"),
                                "sub_title_text","尚未确认成功；重复点击不会重复执行业务。",
                                "card_action",Map.of("type",0))));
    }
    static Map<String, Object> welcomeFrame(String id) {
        return Map.of("cmd", "aibot_respond_welcome_msg", "headers", Map.of("req_id", id),
                "body", Map.of("msgtype", "text", "text", Map.of("content",
                        "你好，我是英格云企业微信助手。发送 /help 查看自然语言、CRM 和成单操作教程。")));
    }

    private void receive(WebSocket source, String payload) {
        if (socket.get() != source) return;
        try {
            JsonNode frame = json.readTree(payload);
            String cmd = frame.path("cmd").asText("");
            String id = frame.path("headers").path("req_id").asText("");
            if (cmd.isBlank()) {
                int code = frame.path("errcode").asInt(-1);
                if (id.equals(subscribeId.get())) {
                    if (code == 0) {
                        status.connected(); reconnectAttempts.set(0); missedHeartbeats.set(0); authenticationFailures.set(0);
                    } else {
                        authenticationFailures.incrementAndGet();
                        disconnect(source, "WECOM_AIBOT_SUBSCRIBE_REJECTED");
                        if (authenticationFailures.get() >= 5)
                            status.unavailable("AUTH_FAILED", "WECOM_AUTH_RETRY_EXHAUSTED");
                    }
                } else if (id.equals(heartbeatId.get()) && code == 0) missedHeartbeats.set(0);
                else acks.ack(id, code);
                return;
            }
            // Only authenticated official socket callbacks may attest that WeCom already handled group @ routing.
            if (!status.isConnected()) return;
            if ("aibot_event_callback".equals(cmd)) {
                String event = frame.path("body").path("event").path("eventtype").asText();
                if ("disconnected_event".equals(event)) {
                    displaced.set(true); disconnect(source, "WECOM_SERVER_DISPLACED");
                    status.unavailable("DISPLACED", "WECOM_SERVER_DISPLACED"); return;
                }
                if ("enter_chat".equals(event)) { sendAcknowledged(welcomeFrame(id)); return; }
                if (!"template_card_event".equals(event)) return;
            } else if (!"aibot_msg_callback".equals(cmd)) return;
            if (!frame.path("body").isObject() || id.isBlank()) return;
            ObjectNode body = frame.path("body").deepCopy(); body.put("req_id", id);
            String user = body.path("from").path("userid").asText("");
            if (user.isBlank()) return;
            String messageId=body.path("msgid").asText(id);
            boolean cardCallback="aibot_event_callback".equals(cmd);
            String explicitChatType=body.path("chattype").asText("");
            String suppliedChatId=body.path("chatid").asText("");
            // Official card callbacks may omit chattype; mirror ingress inference without altering business namespaces.
            boolean privateChat="single".equals(explicitChatType)
                    || (cardCallback && explicitChatType.isBlank() && suppliedChatId.isBlank());
            String destination=privateChat?"single:"+user:suppliedChatId;
            if (cardCallback) {
                if (!commands.maySendCardProgress(settings.organizationId(),body)) return;
                sendAcknowledged(cardProgressFrame(id,body.path("event").path("task_id").asText(),user));
            }
            if ("aibot_msg_callback".equals(cmd) && commands.maySendProgress(settings.organizationId(),body)) {
                // Respond inside the initial response window; final durable reply updates this same stream ID.
                sendStream(id,messageId,"正在处理，请稍候；尚未完成的操作不会被视为成功。",false);
            }
            ThreadPoolExecutor worker = stripes[Math.floorMod(user.hashCode(), stripes.length)];
            try {
                worker.execute(() -> {
                    try {
                        commands.handleAuthenticatedCallback(settings.organizationId(), body).ifPresent(reply -> {
                            String msgId = body.path("msgid").asText(id);
                            if (cardCallback) outbox.enqueue(settings.organizationId(),destination,"reply:"+msgId,reply);
                            else outbox.enqueueReply(settings.organizationId(),destination,
                                    "reply:" + msgId,id,msgId,reply);
                        });
                    } catch (RuntimeException failure) { status.error("WECOM_MESSAGE_PROCESSING_FAILED"); }
                });
            } catch (RejectedExecutionException failure) {
                String reply="当前处理队列已满，请稍后重试；此次操作尚未执行。";
                if (cardCallback) outbox.enqueue(settings.organizationId(),destination,"capacity:"+messageId,reply);
                else sendReply(id,messageId,reply);
            }
        } catch (RuntimeException | java.io.IOException failure) {
            status.error("WECOM_AIBOT_MESSAGE_INVALID");
        }
    }

    private void sendRaw(WebSocket current, Object frame) {
        try {
            current.sendText(json.writeValueAsString(frame), true).whenComplete((ignored, failure) -> {
                if (failure != null) disconnect(current, "WECOM_AIBOT_SEND_FAILED");
            });
        } catch (java.io.IOException failure) { throw new IllegalStateException("WECOM_FRAME_ENCODING_FAILED"); }
    }
    private void backoff() {
        retryAt = Instant.now().plusSeconds(Math.min(60, 1L << Math.min(6, reconnectAttempts.incrementAndGet())));
    }
    private void disconnect(WebSocket current, String code) {
        if (!socket.compareAndSet(current, null)) return;
        current.abort(); subscribeId.set(null); heartbeatId.set(null);
        acks.clear("WECOM_ACK_UNCERTAIN"); status.disconnected(code); backoff();
    }

    static String limitUtf8(String text) {
        String value = text == null ? "" : text;
        if (value.getBytes(StandardCharsets.UTF_8).length <= 20000) return value;
        int end = 0, bytes = 0;
        while (end < value.length()) {
            int point = value.codePointAt(end);
            int count = point < 0x80 ? 1 : point < 0x800 ? 2 : point < 0x10000 ? 3 : 4;
            if (bytes + count > 19800) break;
            bytes += count; end += Character.charCount(point);
        }
        return value.substring(0, end) + "\n内容较长，请进入完整 CRM 查看。";
    }
    private static String safeId(String value) {
        String safe = value == null ? UUID.randomUUID().toString() : value.replaceAll("[^A-Za-z0-9_-]", "");
        return safe.isEmpty() ? UUID.randomUUID().toString() : safe.substring(0, Math.min(safe.length(), 48));
    }
    @PreDestroy public void close() {
        stopped.set(true); WebSocket current = socket.get();
        if (current != null) disconnect(current, "WECOM_SHUTDOWN");
        for (ThreadPoolExecutor worker : stripes) worker.shutdownNow();
        timers.shutdownNow();
        if (settings.configured()) {
            try { lease.release(settings.botId()); } catch (RuntimeException ignored) { /* lease expires */ }
        }
    }
    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket current) {
            if (stopped.get() || displaced.get() || !socket.compareAndSet(null, current)) { current.abort(); return; }
            current.request(1);
            String id = "aibot_subscribe-" + UUID.randomUUID(); subscribeId.set(id); subscribeSentAt = Instant.now();
            sendRaw(current, subscribeFrame(id, settings.botId(), settings.secret()));
        }
        @Override public CompletionStage<?> onText(WebSocket current, CharSequence data, boolean last) {
            synchronized (buffer) {
                if (buffer.length() + data.length() > 1048576) {
                    buffer.setLength(0); disconnect(current, "WECOM_FRAME_TOO_LARGE");
                    return CompletableFuture.completedFuture(null);
                }
                buffer.append(data);
                if (last) { String payload = buffer.toString(); buffer.setLength(0); receive(current, payload); }
            }
            current.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onPing(WebSocket current, ByteBuffer data) {
            current.request(1); return current.sendPong(data);
        }
        @Override public CompletionStage<?> onClose(WebSocket current, int code, String reason) {
            disconnect(current, "WECOM_AIBOT_CONNECTION_CLOSED"); return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket current, Throwable failure) {
            disconnect(current, "WECOM_AIBOT_CONNECTION_ERROR");
        }
    }
}
