package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yingge.cloud.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WecomAgentTransportTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void actualLoopbackTransportInitializesCallsAndClosesAuthenticatedMcpSession()throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var methods=new CopyOnWriteArrayList<String>();
        server.createContext("/mcp",exchange->{
            if(!"Bearer synthetic-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401,-1);exchange.close();return;
            }
            if("DELETE".equals(exchange.getRequestMethod())) {
                methods.add("DELETE");exchange.sendResponseHeaders(204,-1);exchange.close();return;
            }
            var request=mapper.readTree(exchange.getRequestBody());String method=request.path("method").asText();methods.add(method);
            if("notifications/initialized".equals(method)) {exchange.sendResponseHeaders(202,-1);exchange.close();return;}
            exchange.getResponseHeaders().add("Mcp-Session-Id","synthetic-session");
            String body=switch(method) {
                case "initialize"->"{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-11-25\"}}";
                case "tools/list"->"data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[]}}\n\n";
                default->"data: {\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"isError\":false,\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}\n\n";
            };
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
            exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
        try {
            var transport=new WecomAgentMcpTransport(mapper,server.getAddress().getPort());
            try(var session=transport.open("synthetic-token",Duration.ofSeconds(3))) {
                assertThat(session.request("tools/list",Map.of(),Duration.ofSeconds(3)).path("tools").isArray()).isTrue();
                assertThat(session.request("tools/call",Map.of("name","known","arguments",Map.of()),Duration.ofSeconds(3))
                        .path("isError").asBoolean()).isFalse();
            }
            assertThat(methods).containsExactly("initialize","notifications/initialized","tools/list","tools/call","DELETE");
            assertThatThrownBy(()->transport.open("invalid",Duration.ofSeconds(3))).isInstanceOf(ApiException.class);
        }finally{server.stop(0);}
    }

    @SuppressWarnings("unchecked")
    @Test void modelHttpErrorsAreSanitizedAndNeverRetriedAfterUnknownToolOutcome()throws Exception {
        HttpClient http=mock(HttpClient.class);
        HttpResponse<InputStream> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn(new ByteArrayInputStream("secret server diagnostic".getBytes(StandardCharsets.UTF_8)));
        doReturn(response).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var client=new WecomAgentModelClient(mapper,URI.create("https://ai.example.com/v1/chat/completions"),"synthetic-key",http);
        assertThatThrownBy(()->client.complete(List.of(),List.of(),Duration.ofSeconds(3)))
                .isInstanceOf(ApiException.class).hasMessageNotContaining("secret").hasMessageNotContaining("synthetic-key");
        verify(http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }

    @Test void boundedBodyRejectsOversizedData() {
        assertThatThrownBy(()->WecomAgentHttpBody.read(new ByteArrayInputStream(new byte[1000]),Duration.ofSeconds(1),100))
                .isInstanceOf(Exception.class);
    }
}
