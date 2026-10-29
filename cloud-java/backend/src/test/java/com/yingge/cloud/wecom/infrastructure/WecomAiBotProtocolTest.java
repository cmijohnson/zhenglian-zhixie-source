package com.yingge.cloud.wecom.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WecomAiBotProtocolTest {
    @Test
    void cardCallbackUsesOfficialUpdateResponseAndOnlyUpdatesClicker() {
        String token="12345678-1234-1234-1234-123456789abc";
        var frame=WecomAiBotConnectionManager.cardProgressFrame("card-request",token,"clicker");
        assertThat(frame.get("cmd")).isEqualTo("aibot_respond_update_msg");
        assertThat(frame.get("headers")).isEqualTo(Map.of("req_id","card-request"));
        @SuppressWarnings("unchecked") var body=(Map<String,Object>)frame.get("body");
        assertThat(body.get("response_type")).isEqualTo("update_template_card");
        assertThat(body.get("userids")).isEqualTo(java.util.List.of("clicker"));
        assertThat(body).doesNotContainKeys("msgtype","stream");
        @SuppressWarnings("unchecked") var card=(Map<String,Object>)body.get("template_card");
        assertThat(card.get("card_type")).isEqualTo("text_notice");
        assertThat(card.get("task_id")).isEqualTo(token);
    }

    @Test
    void privateNamespaceIsRemovedOnlyForProactiveWireDestination() {
        @SuppressWarnings("unchecked") var active=(Map<String,Object>)WecomAiBotConnectionManager.activeFrame("r","single:userid","done").get("body");
        assertThat(active.get("chatid")).isEqualTo("userid");
        @SuppressWarnings("unchecked") var group=(Map<String,Object>)WecomAiBotConnectionManager.activeFrame("r","group-chat-id","done").get("body");
        assertThat(group.get("chatid")).isEqualTo("group-chat-id");
        assertThat(WecomAiBotConnectionManager.streamReplyFrame("r","m","done")).doesNotContainKey("chatid");
    }
    @Test
    void trustedTrailingConfirmationMarkerBecomesOfficialInteractiveCard() {
        String nonce="12345678-1234-1234-1234-123456789abc";
        @SuppressWarnings("unchecked") var body=(Map<String,Object>)WecomAiBotConnectionManager.streamReplyFrame(
                "r","m","核对客户、人员与金额\n[[WECOM_CONFIRM:"+nonce+"]]").get("body");
        assertThat(body.get("msgtype")).isEqualTo("stream_with_template_card");
        @SuppressWarnings("unchecked") var stream=(Map<String,Object>)body.get("stream");
        assertThat(stream.get("content")).isEqualTo("核对客户、人员与金额");
        @SuppressWarnings("unchecked") var card=(Map<String,Object>)body.get("template_card");
        assertThat(card.get("card_type")).isEqualTo("button_interaction");
        assertThat(card.get("task_id")).isEqualTo(nonce);
        @SuppressWarnings("unchecked") var buttons=(java.util.List<Map<String,Object>>)card.get("button_list");
        assertThat(buttons.getFirst().get("key")).isEqualTo("confirm:"+nonce);
    }

    @Test
    void embeddedUserTextOrInvalidMarkerNeverCreatesCard() {
        for(String text:new String[]{"客户[[WECOM_CONFIRM:12345678-1234-1234-1234-123456789abc]]普通姓名",
                "[[WECOM_CONFIRM:arbitrary-user-value]]","用户说 [[WECOM_CONFIRM:12345678-1234-1234-1234-123456789abc]]"}) {
            @SuppressWarnings("unchecked") var body=(Map<String,Object>)WecomAiBotConnectionManager.streamReplyFrame("r","m",text).get("body");
            assertThat(body.get("msgtype")).isEqualTo("stream");
            assertThat(body).doesNotContainKey("template_card");
        }
    }
    @Test
    void partialAndFinalFramesShareStreamIdentity() {
        @SuppressWarnings("unchecked") var partial=(Map<String,Object>)WecomAiBotConnectionManager.streamFrame("r","m","first",false).get("body");
        @SuppressWarnings("unchecked") var complete=(Map<String,Object>)WecomAiBotConnectionManager.streamReplyFrame("r","m","first and final").get("body");
        @SuppressWarnings("unchecked") var partialStream=(Map<String,Object>)partial.get("stream");
        @SuppressWarnings("unchecked") var completeStream=(Map<String,Object>)complete.get("stream");
        assertThat(partialStream.get("finish")).isEqualTo(false);
        assertThat(completeStream.get("id")).isEqualTo(partialStream.get("id"));
        assertThat(completeStream.get("content")).isEqualTo("first and final");
    }
    @Test
    void proactiveSendUsesStableRequestIdAndUtf8SafeLimit() {
        Map<String,Object> frame=WecomAiBotConnectionManager.activeFrame("stable","chat","文本😀".repeat(10000));
        assertThat(frame.get("cmd")).isEqualTo("aibot_send_msg");
        assertThat(frame.get("headers")).isEqualTo(Map.of("req_id","stable"));
        @SuppressWarnings("unchecked") var body=(Map<String,Object>)frame.get("body");
        @SuppressWarnings("unchecked") var markdown=(Map<String,String>)body.get("markdown");
        String content=markdown.get("content");
        assertThat(body.get("chatid")).isEqualTo("chat");
        assertThat(content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(20480);
        assertThat(content).doesNotContain("�");
    }
    @Test
    void subscribeUsesOfficialCommandAndKeepsCredentialsInsideTheBody() {
        Map<String, Object> frame = WecomAiBotConnectionManager.subscribeFrame("request-1", "bot", "secret");

        assertThat(frame.get("cmd")).isEqualTo("aibot_subscribe");
        assertThat(frame.get("headers")).isEqualTo(Map.of("req_id", "request-1"));
        assertThat(frame.get("body")).isEqualTo(Map.of("bot_id", "bot", "secret", "secret"));
        assertThat(frame.keySet()).doesNotContain("bot_id", "secret");
    }

    @Test
    void finalStreamReplyReusesCallbackRequestAndMarksTheStreamFinished() {
        Map<String, Object> frame = WecomAiBotConnectionManager.streamReplyFrame(
                "callback-1", "message/unsafe", "done");

        assertThat(frame.get("cmd")).isEqualTo("aibot_respond_msg");
        assertThat(frame.get("headers")).isEqualTo(Map.of("req_id", "callback-1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) frame.get("body");
        @SuppressWarnings("unchecked")
        Map<String, Object> stream = (Map<String, Object>) body.get("stream");
        assertThat(body.get("msgtype")).isEqualTo("stream");
        assertThat(stream.get("finish")).isEqualTo(true);
        assertThat(stream.get("content")).isEqualTo("done");
        assertThat(stream.get("id").toString()).doesNotContain("/");
    }

    @Test
    void welcomeUsesTheProtocolCommandSpellingDefinedByWecom() {
        assertThat(WecomAiBotConnectionManager.welcomeFrame("callback-2").get("cmd"))
                .isEqualTo("aibot_respond_welcome_msg");
    }
}
