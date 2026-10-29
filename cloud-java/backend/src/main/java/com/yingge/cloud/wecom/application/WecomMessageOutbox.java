package com.yingge.cloud.wecom.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Enqueue participates in the caller's business transaction; no network or connector dependency. */
@Service
public class WecomMessageOutbox {
    private final JdbcTemplate jdbc;
    private final WecomAgentCipher cipher;
    public WecomMessageOutbox(JdbcTemplate jdbc, WecomAgentCipher cipher) { this.jdbc = jdbc; this.cipher = cipher; }

    @Transactional
    public UUID enqueue(UUID organizationId, String chatId, String dedupKey, String text) {
        return insert(organizationId, chatId, dedupKey, null, null, text);
    }

    @Transactional
    public UUID enqueueReply(UUID organizationId, String chatId, String dedupKey,
            String requestId, String messageId, String text) {
        return insert(organizationId, chatId, dedupKey, requestId, messageId, text);
    }

    private UUID insert(UUID organizationId, String chatId, String dedupKey, String callbackId, String messageId, String text) {
        if (organizationId == null || chatId == null || chatId.isBlank() || chatId.length() > 256
                || dedupKey == null || dedupKey.isBlank() || dedupKey.length() > 256
                || text == null || text.isBlank()) throw new IllegalArgumentException("WECOM_OUTBOX_INVALID");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into wecom_ai_bot_message_outbox(id,organization_id,chat_id,dedup_key,request_id,
                    callback_request_id,callback_message_id,encrypted_payload,status,attempts,next_attempt_at,created_at,updated_at)
                values (?,?,?,?,?,?,?,?,'PENDING',0,now(),now(),now())
                on conflict(organization_id,dedup_key) do nothing
                """, id, organizationId, chatId, dedupKey, "aibot_send_msg-" + id,
                callbackId, messageId, cipher.encryptBytes(text));
        return jdbc.queryForObject("select id from wecom_ai_bot_message_outbox where organization_id=? and dedup_key=?",
                UUID.class, organizationId, dedupKey);
    }
}
