package com.yingge.cloud.wecom.application;

import com.yingge.cloud.deal.application.DealConfirmationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Per-day outbox keys deduplicate this scheduler across all backend replicas. */
@Component
public class WecomDealLeaderboardScheduler {
    private static final Logger log = LoggerFactory.getLogger(WecomDealLeaderboardScheduler.class);
    private final JdbcTemplate jdbc;
    private final DealConfirmationService deals;
    private final WecomMessageOutbox outbox;

    public WecomDealLeaderboardScheduler(JdbcTemplate jdbc, DealConfirmationService deals, WecomMessageOutbox outbox) {
        this.jdbc=jdbc;this.deals=deals;this.outbox=outbox;
    }

    @Scheduled(cron="0 0 22 * * *",zone="Asia/Shanghai")
    public void publish() {
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var settings=jdbc.query("""
                select organization_id,broadcast_chat_id from wecom_agent_settings
                where enabled=true and full_data_risk_accepted_at is not null
                    and broadcast_chat_id is not null and broadcast_chat_id<>''
                """,(rs,row)->new Target(rs.getObject(1,UUID.class),rs.getString(2)));
        for(var target:settings) {
            try {
                outbox.enqueue(target.org,target.chat,"daily-leaderboard:"+date,
                        deals.leaderboardFor(target.org,date).previewText());
            } catch(RuntimeException exception) {
                // Do not log provider messages, customer data or message bodies.
                log.warn("Enterprise WeChat leaderboard enqueue failed for organization {} ({})",
                        target.org,exception.getClass().getSimpleName());
            }
        }
    }
    private record Target(UUID org,String chat){}
}
