package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Actual authenticated MCP transport: does not invoke handlers with invented scope contexts. */
@Component
public class WecomAgentMcpTransport {
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public WecomAgentMcpTransport(ObjectMapper mapper,@Value("${server.port:8080}") int port) {
        this.mapper=mapper;this.endpoint=URI.create("http://127.0.0.1:"+port+"/mcp");
    }
    public Connection open(String token,Duration timeout) {
        try {
            var reply=send(token,null,Map.of("jsonrpc","2.0","id",1,"method","initialize","params",
                    Map.of("protocolVersion","2025-11-25","capabilities",Map.of(),"clientInfo",
                            Map.of("name","yingge-wecom-agent","version","1.0"))),timeout);
            String session=reply.headers().firstValue("Mcp-Session-Id").orElseThrow();
            send(token,session,Map.of("jsonrpc","2.0","method","notifications/initialized"),timeout);
            return new Session(token,session);
        }catch(Exception e){throw failure();}
    }
    public interface Connection extends AutoCloseable {
        JsonNode request(String method,Object params,Duration timeout);
        @Override void close();
    }
    private final class Session implements Connection {
        private final String token,session;
        private int id=1;
        private Session(String token,String session){this.token=token;this.session=session;}
        public JsonNode request(String method,Object params,Duration timeout) {
            try {
                var response=send(token,session,Map.of("jsonrpc","2.0","id",++id,"method",method,"params",params),timeout);
                JsonNode result=parse(mapper,response.body());
                if(result.has("error"))throw failure();
                return result.path("result");
            }catch(ApiException e){throw e;}catch(Exception e){throw failure();}
        }
        @Override public void close() {
            try { http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(3))
                    .header("Authorization","Bearer "+token).header("Mcp-Session-Id",session)
                    .DELETE().build(),HttpResponse.BodyHandlers.discarding()); }
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception ignored){}
        }
        @Override public String toString(){return "McpSession[credentials=redacted]";}
    }
    private Reply send(String token,String session,Object request,Duration timeout)throws Exception {
        long started=System.nanoTime();
        var builder=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization","Bearer "+token)
                .header("Accept","application/json, text/event-stream").header("Content-Type","application/json")
                .header("MCP-Protocol-Version","2025-11-25");
        if(session!=null)builder.header("Mcp-Session-Id",session);
        var response=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request))).build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var stream=response.body()) {
            if(response.statusCode()!=200&&response.statusCode()!=202)throw failure();
            Duration remaining=timeout.minusNanos(System.nanoTime()-started);
            if(remaining.isZero()||remaining.isNegative())throw failure();
            return new Reply(response.headers(),WecomAgentHttpBody.read(stream,remaining,2_000_000));
        }
    }
    static JsonNode parse(ObjectMapper mapper,String body)throws Exception {
        for(String line:body.split("\\R"))if(line.startsWith("data:")) {
            JsonNode node=mapper.readTree(line.substring(5).trim());
            if(node.has("result")||node.has("error"))return node;
        }
        return mapper.readTree(body);
    }
    private static ApiException failure(){return ApiException.unavailable("WECOM_AGENT_MCP_UNAVAILABLE","MCP 连接或权限校验失败，请重新授权或稍后重试");}
    private record Reply(java.net.http.HttpHeaders headers,String body){}
}
