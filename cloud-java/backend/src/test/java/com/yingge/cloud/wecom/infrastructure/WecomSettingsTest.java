package com.yingge.cloud.wecom.infrastructure;

import com.yingge.cloud.wecom.application.WecomAiBotSettings;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class WecomSettingsTest {
    @Test void allowsOnlyExactOfficialSecureWebsocketOrigin() {
        assertThat(settings("wss://openws.work.weixin.qq.com").configured()).isTrue();
        for(String endpoint:new String[]{"ws://openws.work.weixin.qq.com","wss://openws.work.weixin.qq.com.evil.test",
                "wss://user:password@openws.work.weixin.qq.com","wss://openws.work.weixin.qq.com?token=x",
                "wss://openws.work.weixin.qq.com:8080","wss://open.work.weixin.qq.com/ws/aibot",
                "wss://openws.work.weixin.qq.com/unregistered"})
            assertThat(settings(endpoint).configured()).as(endpoint).isFalse();
    }
    private WecomAiBotSettings settings(String endpoint) {
        return new WecomAiBotSettings(true,endpoint,"test-bot","test-secret",UUID.randomUUID().toString(),"https://example.test/");
    }
}
