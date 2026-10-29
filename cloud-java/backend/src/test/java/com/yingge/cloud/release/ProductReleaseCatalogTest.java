package com.yingge.cloud.release;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProductReleaseCatalogTest {
    private static final String CURRENT_VERSION = "2026.09.28.2";

    @Test
    void currentBuildVersionHasPublishedDetailedReleaseNotes() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V65__wecom_agent_dual_mode.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String application;
        try (var input = getClass().getResourceAsStream("/application.yml")) {
            assertThat(input).isNotNull();
            application = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(application).contains("APP_RELEASE_VERSION:" + CURRENT_VERSION);
        assertThat(migration).contains("'" + CURRENT_VERSION + "'")
                .contains("'企业微信自然语言与 Slash 双模式助手'")
                .contains("Slash 与自然语言共享客户选择和成单草稿")
                .contains("转接和成单必须本人确认")
                .contains("敏感数据加密保存");
        assertThat(migration.split("INSERT INTO product_releases", -1)).hasSize(2);
        assertThat(migration).contains("000000000035");
    }

    @Test
    void androidDownloadReleaseStillContainsSignedArtifactMetadata() throws Exception {
        String migration;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V56__android_update_delivery_and_web_download.sql")) {
            assertThat(input).isNotNull();
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(migration).contains("'2026.09.03.2'")
                .contains("'Android 更新下载与飞书入口修复'")
                .contains("000000000027")
                .contains("'0.2.3'")
                .contains("ac1caf1fab523b70fd76e6bbc7f839e1f289ff745a90c87566afad062b25bf3a");
    }
}
