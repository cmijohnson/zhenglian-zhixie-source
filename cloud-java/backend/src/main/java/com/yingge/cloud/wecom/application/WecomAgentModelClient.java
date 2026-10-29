package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yingge.cloud.shared.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Separate model channel; never falls back to organization AI settings. */
@Component
public class WecomAgentModelClient {
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String key;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public WecomAgentModelClient(ObjectMapper mapper,
            @Value("${app.wecom.aibot.agent.base-url:http://ai.example.com}") String base,
            @Value("${app.wecom.aibot.agent.api-key:}") String key) {
        this(mapper,normalize(base),key,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }
    WecomAgentModelClient(ObjectMapper mapper,URI endpoint,String key,HttpClient http) {
        this.mapper=mapper;this.endpoint=endpoint;this.key=key==null?"":key.trim();this.http=http;
    }
    public boolean configured(){return !key.isBlank();}

    public JsonNode complete(List<JsonNode> messages,List<JsonNode> tools,Duration timeout) {
        if(!configured()) throw ApiException.unavailable("WECOM_AGENT_MODEL_NOT_CONFIGURED","对话模型未配置，请使用 /help 中的命令");
        try {
            long started=System.nanoTime();
            String payload=mapper.writeValueAsString(Map.of("model","MiniMax-M3","messages",messages,
                    "tools",tools,"tool_choice","auto","stream",true,"max_tokens",4096));
            var request=HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization","Bearer "+key)
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                if(response.statusCode()==429) throw ApiException.unavailable("WECOM_AGENT_RATE_LIMITED","模型忙碌，请稍后重试或使用 Slash 命令");
                if(response.statusCode()<200||response.statusCode()>=300) throw ApiException.unavailable("WECOM_AGENT_MODEL_FAILED","模型请求失败，请使用 Slash 命令；已完成操作不会重复执行");
                Duration remaining=timeout.minusNanos(System.nanoTime()-started);
                if(remaining.isNegative()||remaining.isZero())throw new IllegalArgumentException("model deadline");
                return parseResponse(mapper,WecomAgentHttpBody.read(stream,remaining,2_000_000));
            }
        } catch(ApiException e){throw e;}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw ApiException.unavailable("WECOM_AGENT_INTERRUPTED","对话已中断，请查看当前草稿");}
        catch(Exception e){throw ApiException.unavailable("WECOM_AGENT_MODEL_FAILED","模型连接暂不可用，请使用 Slash 命令；不要重复确认已完成操作");}
    }

    static URI normalize(String raw) {
        try {
            URI uri=URI.create(raw.trim());
            if(!"ai.example.com".equalsIgnoreCase(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null
                    ||uri.getQuery()!=null||uri.getFragment()!=null||!List.of("http","https").contains(uri.getScheme())
                    ||!List.of("","/","/v1","/v1/").contains(uri.getPath())) throw new IllegalArgumentException();
            return URI.create(uri.getScheme()+"://ai.example.com/v1/chat/completions");
        }catch(RuntimeException e){throw new IllegalArgumentException("WECOM_AGENT base URL must be the configured exact NewAPI origin with optional /v1");}
    }

    /** Gather fragmented function arguments fully before any tool is executed. */
    static JsonNode parseResponse(ObjectMapper mapper,String body)throws Exception {
        if(!body.stripLeading().startsWith("data:")&&!body.contains("\ndata:")) {
            JsonNode message=mapper.readTree(body).at("/choices/0/message");
            if(!message.isObject())throw new IllegalArgumentException("missing model message");
            return validateMessage(message);
        }
        ObjectNode result=mapper.createObjectNode().put("role","assistant");
        StringBuilder content=new StringBuilder(),reasoning=new StringBuilder();
        Map<Integer,ObjectNode> calls=new TreeMap<>();
        ArrayNode details=mapper.createArrayNode();
        boolean done=false;
        for(String line:body.split("\\R")) {
            if(!line.startsWith("data:"))continue;
            String data=line.substring(5).trim();
            if("[DONE]".equals(data)){done=true;break;}
            JsonNode chunk=mapper.readTree(data);
            if(chunk.has("error"))throw new IllegalArgumentException("stream error");
            JsonNode delta=chunk.at("/choices/0/delta");
            if(delta.path("content").isTextual())content.append(delta.path("content").asText());
            if(delta.path("reasoning_content").isTextual())reasoning.append(delta.path("reasoning_content").asText());
            if(delta.path("reasoning_details").isArray())delta.path("reasoning_details").forEach(details::add);
            for(JsonNode partial:delta.path("tool_calls")) {
                int index=partial.path("index").asInt(-1);
                if(index<0||index>11)throw new IllegalArgumentException("invalid call index");
                ObjectNode call=calls.computeIfAbsent(index,i->mapper.createObjectNode().put("type","function"));
                if(partial.has("id"))call.put("id",partial.path("id").asText());
                ObjectNode fn=call.withObject("function");
                for(String field:List.of("name","arguments")) if(partial.path("function").has(field))
                    fn.put(field,fn.path(field).asText("")+partial.path("function").path(field).asText());
            }
        }
        if(!done)throw new IllegalArgumentException("incomplete model stream");
        result.put("content",content.toString());
        if(!reasoning.isEmpty())result.put("reasoning_content",reasoning.toString());
        if(!details.isEmpty())result.set("reasoning_details",details);
        if(!calls.isEmpty()){ArrayNode array=result.putArray("tool_calls");calls.values().forEach(array::add);}
        return validateMessage(result);
    }
    private static JsonNode validateMessage(JsonNode message) {
        if(!"assistant".equals(message.path("role").asText()))throw new IllegalArgumentException("Invalid assistant role");
        var ids=new java.util.HashSet<String>();
        for(JsonNode call:message.path("tool_calls")) {
            String id=call.path("id").asText(),name=call.path("function").path("name").asText();
            if(id.isBlank()||id.length()>200||!ids.add(id)||!"function".equals(call.path("type").asText())
                    ||name.isBlank()||name.length()>128||!call.path("function").path("arguments").isTextual()
                    ||call.path("function").path("arguments").asText().length()>32000)
                throw new IllegalArgumentException("Invalid function call");
        }
        return message;
    }
}
