package com.yingge.cloud.wecom;

import com.yingge.cloud.wecom.infrastructure.WecomAiBotConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WecomAiBotConfigurationContractTest {
    @Test
    void connectorIsDisabledByDefaultAndUsesEnvironmentBackedSettings() throws Exception {
        ConditionalOnProperty condition = WecomAiBotConnectionManager.class
                .getAnnotation(ConditionalOnProperty.class);
        assertThat(condition.name()).containsExactly("app.wecom.aibot.enabled");
        assertThat(condition.havingValue()).isEqualTo("true");

        String yaml = new String(new ClassPathResource("application.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(yaml).contains("enabled: ${WECOM_AIBOT_ENABLED:false}");
        assertThat(yaml).contains("bot-id: ${WECOM_AIBOT_BOT_ID:}");
        assertThat(yaml).contains("secret: ${WECOM_AIBOT_SECRET:}");
        assertThat(yaml).contains("endpoint: ${WECOM_AIBOT_ENDPOINT:wss://openws.work.weixin.qq.com}");
    }
}
