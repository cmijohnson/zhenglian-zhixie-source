package com.yingge.cloud.wecom.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WecomAiBotCommandParsingTest {
    @Test
    void extractsSlashCommandAfterAnAtMention() {
        String command = WecomAiBotCommandService.normalizeCommand("@yingge /search 院校甲");

        assertThat(WecomAiBotCommandService.verb(command)).isEqualTo("/search");
        assertThat(WecomAiBotCommandService.args(command)).isEqualTo("院校甲");
    }

    @Test
    void supportsNaturalLanguageEntryAliasesAndBlankHelp() {
        assertThat(WecomAiBotCommandService.normalizeCommand("打开CRM")).isEqualTo("/crm");
        assertThat(WecomAiBotCommandService.normalizeCommand("快捷成单")).isEqualTo("/deal-web");
        assertThat(WecomAiBotCommandService.normalizeCommand("  ")).isEqualTo("/help");
    }

    @Test
    void onlyRemovesLeadingBotMentionAndNeverTreatsAnEmbeddedUrlAsSlash() {
        assertThat(WecomAiBotCommandService.normalizeCommand("请分析 https://example.invalid/a"))
                .isEqualTo("请分析 https://example.invalid/a");
        assertThat(WecomAiBotCommandService.stripBotMention("@机器人 /primary @成员甲", "bot"))
                .isEqualTo("/primary @成员甲");
        assertThat(WecomAiBotCommandService.stripBotMention("<@bot> /assist <@member>", "bot"))
                .isEqualTo("/assist <@member>");
        assertThat(WecomAiBotCommandService.stripBotMention("@英格云小格同学 /select 2", "bot"))
                .isEqualTo("/select 2");
        assertThat(WecomAiBotCommandService.normalizeCommand("@英格云小格同学 /detail"))
                .isEqualTo("/detail");
        assertThat(WecomAiBotCommandService.verb("/SEARCH\t客户")).isEqualTo("/search");
        assertThat(WecomAiBotCommandService.args("/search\t客户")).isEqualTo("客户");
    }

    @Test
    void aliasesRequireDeterministicConfirmationAndUtf8RepliesFitProtocolLimit() {
        assertThat(WecomAiBotCommandService.normalizeCommand("确认成单")).isEqualTo("/confirm");
        assertThat(WecomAiBotCommandService.normalizeCommand("成单")).isEqualTo("/deal");
        assertThat(WecomAiBotCommandService.normalizeCommand("修改 成单人 @成员")).isEqualTo("/primary @成员");
        assertThat(WecomAiBotCommandService.normalizeCommand("修改 分配 @成员=300")).isEqualTo("/split @成员=300");
        assertThat(WecomAiBotCommandService.normalizeCommand("搜索\t客户")).isEqualTo("/search 客户");
        String limited=WecomAiBotCommandService.limit("客户🙂".repeat(10000));
        assertThat(limited.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(20480);
    }
}
