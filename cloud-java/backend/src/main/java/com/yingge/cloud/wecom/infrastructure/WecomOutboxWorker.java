package com.yingge.cloud.wecom.infrastructure;

import com.yingge.cloud.wecom.application.WecomAiBotSettings;
import com.yingge.cloud.wecom.application.WecomAgentCipher;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name="app.wecom.aibot.enabled",havingValue="true")
public class WecomOutboxWorker {
    private final JdbcTemplate jdbc;
    private final WecomAiBotConnectionManager connection;
    private final WecomAiBotSettings settings;
    private final WecomAgentCipher cipher;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1),Thread.ofPlatform().name("wecom-outbox-",0).factory(),new ThreadPoolExecutor.AbortPolicy());
    public WecomOutboxWorker(JdbcTemplate jdbc, WecomAiBotConnectionManager connection,
            WecomAiBotSettings settings, WecomAgentCipher cipher) {
        this.jdbc=jdbc; this.connection=connection; this.settings=settings; this.cipher=cipher;
    }

    @Scheduled(fixedDelay=1000,initialDelay=5000)
    public void deliver() {
        if (!connection.ready() || !running.compareAndSet(false,true)) return;
        try { sender.execute(this::process); }
        catch (java.util.concurrent.RejectedExecutionException failure) { running.set(false); }
    }

    private void process() {
        try {
            // A process can die after remote delivery. Never automatically resend an ambiguous broadcast.
            jdbc.update("""
                    update wecom_ai_bot_message_outbox set status='UNCERTAIN',error_code='WECOM_PROCESS_INTERRUPTED',updated_at=now()
                    where organization_id=? and status='SENDING' and lease_until < now()
                    """, settings.organizationId());
            var rows = jdbc.query("""
                    with picked as (select id from wecom_ai_bot_message_outbox
                        where organization_id=? and status='PENDING' and next_attempt_at<=now()
                          and created_at >= now()-interval '24 hours'
                        order by created_at limit 1 for update skip locked)
                    update wecom_ai_bot_message_outbox item set status='SENDING',attempts=attempts+1,
                        lease_until=now()+interval '30 seconds',updated_at=now()
                    from picked where item.id=picked.id returning item.*
                    """, (rs,i) -> new Pending(rs.getObject("id",UUID.class),rs.getString("chat_id"),
                    rs.getString("request_id"),rs.getString("callback_request_id"),rs.getString("callback_message_id"),
                    rs.getBytes("encrypted_payload"),rs.getInt("attempts")),settings.organizationId());
            if (rows.isEmpty()) return;
            Pending row=rows.getFirst();
            try {
                String text=cipher.decryptBytes(row.payload);
                var send = row.callbackId == null ? connection.sendActive(row.requestId,row.chatId,text)
                        : connection.sendReply(row.callbackId,row.messageId,text);
                send.get(7,TimeUnit.SECONDS);
                jdbc.update("update wecom_ai_bot_message_outbox set status='SENT',sent_at=now(),updated_at=now(),lease_until=null,error_code=null where id=? and status='SENDING'",row.id);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                Throwable cause=failure;
                while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause()!=null) cause=cause.getCause();
                String code=cause.getMessage();
                boolean uncertain=code==null || code.contains("UNCERTAIN") || cause instanceof java.util.concurrent.TimeoutException;
                boolean temporary="WECOM_NOT_CONNECTED".equals(code) || "WECOM_CONNECTION_CHANGED".equals(code) || "WECOM_SEND_QUEUE_FULL".equals(code);
                String state=uncertain?"UNCERTAIN":temporary&&row.attempts<5?"PENDING":"FAILED";
                String safeCode=uncertain?"WECOM_DELIVERY_UNCERTAIN":temporary?"WECOM_DELIVERY_RETRY":"WECOM_DELIVERY_REJECTED";
                jdbc.update("""
                        update wecom_ai_bot_message_outbox set status=?,error_code=?,lease_until=null,
                            next_attempt_at=now()+(?*interval '1 second'),updated_at=now() where id=? and status='SENDING'
                        """,state,safeCode,Math.min(300,1<<Math.min(row.attempts,8)),row.id);
            }
        } finally { running.set(false); }
    }
    @PreDestroy public void close() { sender.shutdownNow(); }
    @Scheduled(fixedDelay=3600000,initialDelay=60000)
    public void expireMessageBodies() {
        if (!settings.configured() || !cipher.configured()) return;
        // Keep stable dedup IDs and delivery evidence, but discard customer-bearing reply/broadcast text.
        jdbc.update("""
                update wecom_ai_bot_message_outbox set encrypted_payload=?,
                    status=case when status in ('PENDING','SENDING') then 'FAILED' else status end,
                    error_code='WECOM_MESSAGE_BODY_EXPIRED',lease_until=null,updated_at=now()
                where organization_id=? and created_at < now()-interval '24 hours'
                  and error_code is distinct from 'WECOM_MESSAGE_BODY_EXPIRED'
                """,cipher.encryptBytes("消息内容已超过保留期限，请在 CRM 核对业务结果。"),settings.organizationId());
    }
    private record Pending(UUID id,String chatId,String requestId,String callbackId,String messageId,byte[] payload,int attempts) {}
}
