package com.yingge.cloud.wecom.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Uses database time so host clock drift cannot elect two active connectors. */
@Component
public class WecomConnectionLease {
    private final JdbcTemplate jdbc;
    private final UUID owner = UUID.randomUUID();

    public WecomConnectionLease(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean renew(String botKey) {
        return jdbc.update("""
                insert into wecom_ai_bot_connection_leases(bot_key,owner_id,expires_at,updated_at)
                values (?,?,now()+interval '45 seconds',now())
                on conflict(bot_key) do update set owner_id=excluded.owner_id,
                    expires_at=excluded.expires_at,updated_at=now()
                where wecom_ai_bot_connection_leases.owner_id=excluded.owner_id
                   or wecom_ai_bot_connection_leases.expires_at < now()
                """, botKey, owner) == 1;
    }

    public void release(String botKey) {
        jdbc.update("delete from wecom_ai_bot_connection_leases where bot_key=? and owner_id=?", botKey, owner);
    }
}
