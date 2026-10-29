package com.yingge.cloud.wecom.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.wecom.application.*;
import org.junit.jupiter.api.Test;
import java.net.http.WebSocket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WecomConnectionLifecycleTest {
    @Test void privateCardMissingChatTypeAndChatIdUsesStablePrivateNamespace() throws Exception {
        var fixture=new Fixture();
        try {
            when(fixture.commands.maySendCardProgress(any(),any())).thenReturn(true);
            when(fixture.commands.handleAuthenticatedCallback(any(),any())).thenReturn(Optional.of("已处理私聊确认"));
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_event_callback\",\"headers\":{\"req_id\":\"private-card\"},\"body\":{\"msgid\":\"private-message\",\"from\":{\"userid\":\"user\"},\"event\":{\"eventtype\":\"template_card_event\",\"event_key\":\"confirm:12345678-1234-1234-1234-123456789abc\",\"task_id\":\"12345678-1234-1234-1234-123456789abc\"}}}");
            verify(fixture.outbox,timeout(1000)).enqueue(any(),eq("single:user"),eq("reply:private-message"),eq("已处理私聊确认"));
            verify(fixture.outbox,never()).enqueue(any(),eq(""),anyString(),anyString());
            verify(fixture.outbox,never()).enqueueReply(any(),anyString(),anyString(),anyString(),anyString(),anyString());
        } finally { fixture.manager.close(); }
    }
    @Test void cardClickUpdatesTemplateImmediatelyAndSendsFinalResultAsActiveMessage() throws Exception {
        var fixture=new Fixture();
        try {
            when(fixture.commands.maySendCardProgress(any(),any())).thenReturn(true);
            when(fixture.commands.handleAuthenticatedCallback(any(),any())).thenReturn(Optional.of("成单已确认"));
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_event_callback\",\"headers\":{\"req_id\":\"card-request\"},\"body\":{\"msgid\":\"card-message\",\"chattype\":\"single\",\"msgtype\":\"event\",\"from\":{\"userid\":\"user\"},\"event\":{\"eventtype\":\"template_card_event\",\"event_key\":\"confirm:12345678-1234-1234-1234-123456789abc\",\"task_id\":\"12345678-1234-1234-1234-123456789abc\"}}}");
            var captured=ArgumentCaptor.forClass(String.class);
            verify(fixture.socket).sendText(captured.capture(),eq(true));
            var frame=new ObjectMapper().readTree(captured.getValue());
            assertThat(frame.path("cmd").asText()).isEqualTo("aibot_respond_update_msg");
            assertThat(frame.path("headers").path("req_id").asText()).isEqualTo("card-request");
            assertThat(frame.path("body").path("userids").get(0).asText()).isEqualTo("user");
            verify(fixture.outbox,timeout(1000)).enqueue(any(),eq("single:user"),eq("reply:card-message"),eq("成单已确认"));
            verify(fixture.outbox,never()).enqueueReply(any(),anyString(),anyString(),anyString(),anyString(),anyString());
            verify(fixture.commands,never()).maySendProgress(any(),any());
        } finally { fixture.manager.close(); }
    }

    @Test void invalidOrUnauthorizedCardProducesNeitherUpdateNorBusinessExecution() throws Exception {
        var fixture=new Fixture();
        try {
            when(fixture.commands.maySendCardProgress(any(),any())).thenReturn(false);
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_event_callback\",\"headers\":{\"req_id\":\"r\"},\"body\":{\"from\":{\"userid\":\"user\"},\"event\":{\"eventtype\":\"template_card_event\"}}}");
            verify(fixture.socket,never()).sendText(anyString(),anyBoolean());
            verify(fixture.commands,never()).handleAuthenticatedCallback(any(),any());
            verifyNoInteractions(fixture.outbox);
        } finally { fixture.manager.close(); }
    }
    @Test void allowedLongOperationGetsInitialUnfinishedStreamBeforeWorkerCompletes() throws Exception {
        var fixture=new Fixture();
        var release=new CountDownLatch(1);
        try {
            when(fixture.commands.maySendProgress(any(),any())).thenReturn(true);
            doAnswer(invocation->{release.await(2,TimeUnit.SECONDS);return Optional.empty();})
                    .when(fixture.commands).handleAuthenticatedCallback(any(),any());
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_msg_callback\",\"headers\":{\"req_id\":\"r\"},\"body\":{\"msgid\":\"m\",\"from\":{\"userid\":\"user\"}}}");
            var captured=ArgumentCaptor.forClass(String.class);
            verify(fixture.socket).sendText(captured.capture(),eq(true));
            var frame=new ObjectMapper().readTree(captured.getValue());
            assertThat(frame.path("cmd").asText()).isEqualTo("aibot_respond_msg");
            assertThat(frame.path("headers").path("req_id").asText()).isEqualTo("r");
            assertThat(frame.path("body").path("stream").path("id").asText()).isEqualTo("yingge-m");
            assertThat(frame.path("body").path("stream").path("finish").asBoolean()).isFalse();
        } finally { release.countDown();fixture.manager.close(); }
    }

    @Test void policyDeniedCallbackDoesNotLeakProgressReply() throws Exception {
        var fixture=new Fixture();
        try {
            when(fixture.commands.maySendProgress(any(),any())).thenReturn(false);
            when(fixture.commands.handleAuthenticatedCallback(any(),any())).thenReturn(Optional.empty());
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_msg_callback\",\"headers\":{\"req_id\":\"r\"},\"body\":{\"msgid\":\"m\",\"from\":{\"userid\":\"user\"}}}");
            verify(fixture.commands,timeout(1000)).handleAuthenticatedCallback(any(),any());
            verify(fixture.socket,never()).sendText(anyString(),anyBoolean());
        } finally { fixture.manager.close(); }
    }
    @Test void callbackBeforeSubscribeAuthenticationCannotReachCommands() throws Exception {
        var fixture=new Fixture();
        try {
            fixture.runtime.connecting();
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_msg_callback\",\"headers\":{\"req_id\":\"r\"},\"body\":{\"from\":{\"userid\":\"user\"}}}");
            verifyNoInteractions(fixture.commands);
        } finally { fixture.manager.close(); }
    }
    @Test void twoMissedHeartbeatAcksCloseZombieSocket() throws Exception {
        var fixture=new Fixture();
        try {
            fixture.manager.heartbeat(); fixture.manager.heartbeat(); fixture.manager.heartbeat();
            verify(fixture.socket).abort();
            assertThat(fixture.manager.ready()).isFalse();
            assertThat(fixture.runtime.response().lastErrorCode()).isEqualTo("WECOM_HEARTBEAT_TIMEOUT");
        } finally { fixture.manager.close(); }
    }

    @Test void serverDisplacementStopsConnectorAndDoesNotProcessOldGeneration() throws Exception {
        var fixture=new Fixture();
        try {
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_event_callback\",\"headers\":{\"req_id\":\"e\"},\"body\":{\"event\":{\"eventtype\":\"disconnected_event\"}}}");
            assertThat(fixture.runtime.response().state()).isEqualTo("DISPLACED");
            fixture.manager.maintainConnection();
            verifyNoInteractions(fixture.lease);
            fixture.receive(fixture.socket,"{\"cmd\":\"aibot_msg_callback\",\"headers\":{\"req_id\":\"r\"},\"body\":{\"from\":{\"userid\":\"user\"}}}");
            verifyNoInteractions(fixture.commands);
        } finally { fixture.manager.close(); }
    }

    private static class Fixture {
        final WecomAiBotCommandService commands=mock(WecomAiBotCommandService.class);
        final WecomConnectionLease lease=mock(WecomConnectionLease.class);
        final WebSocket socket=mock(WebSocket.class);
        final WecomMessageOutbox outbox=mock(WecomMessageOutbox.class);
        final WecomAiBotRuntimeStatus runtime;
        final WecomAiBotConnectionManager manager;
        Fixture() throws Exception {
            var settings=new WecomAiBotSettings(true,"wss://openws.work.weixin.qq.com","test","test",UUID.randomUUID().toString(),"");
            runtime=new WecomAiBotRuntimeStatus(settings); runtime.connected();
            manager=new WecomAiBotConnectionManager(settings,runtime,commands,new ObjectMapper(),lease,outbox);
            when(socket.sendText(anyString(),eq(true))).thenReturn(CompletableFuture.completedFuture(socket));
            var field=WecomAiBotConnectionManager.class.getDeclaredField("socket"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var reference=(AtomicReference<WebSocket>)field.get(manager);
            reference.set(socket);
        }
        void receive(WebSocket source,String payload) throws Exception {
            var method=WecomAiBotConnectionManager.class.getDeclaredMethod("receive",WebSocket.class,String.class);
            method.setAccessible(true); method.invoke(manager,source,payload);
        }
    }
}
