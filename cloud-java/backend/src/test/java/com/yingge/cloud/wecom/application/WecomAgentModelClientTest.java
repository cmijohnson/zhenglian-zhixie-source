package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WecomAgentModelClientTest {
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void normalizesOnlyTheApprovedOrigin() {
        assertThat(WecomAgentModelClient.normalize("http://ai.example.com").toString())
                .isEqualTo("http://ai.example.com/v1/chat/completions");
        assertThat(WecomAgentModelClient.normalize("https://ai.example.com/v1/").getScheme()).isEqualTo("https");
        for(String bad:new String[]{"http://evil.test","http://ai.example.com@evil.test","http://ai.example.com/path",
                "http://ai.example.com?key=test","http://ai.example.com:1234","ftp://ai.example.com"})
            assertThatThrownBy(()->WecomAgentModelClient.normalize(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void collectsToolFragmentsAndPreservesPrivateReasoningWithoutExecutingPartialArguments()throws Exception {
        String stream="""
                data: {"choices":[{"delta":{"reasoning_content":"private thought","tool_calls":[{"index":0,"id":"call1","function":{"name":"yingge_customer_search","arguments":"{\\\"search\\\":"}}]}}]}

                data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\\"张同学\\\"}"}}]}}]}

                data: [DONE]
                """;
        var message=WecomAgentModelClient.parseResponse(mapper,stream);
        assertThat(message.path("reasoning_content").asText()).isEqualTo("private thought");
        assertThat(message.at("/tool_calls/0/function/name").asText()).isEqualTo("yingge_customer_search");
        assertThat(mapper.readTree(message.at("/tool_calls/0/function/arguments").asText()).path("search").asText()).isEqualTo("张同学");
        assertThat(message.path("content").asText()).doesNotContain("private thought");
        assertThatThrownBy(()->WecomAgentModelClient.parseResponse(mapper,stream.replace("data: [DONE]","")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void parsesNonStreamingCompatibleMessagesAndRejectsInvalidJson()throws Exception {
        assertThat(WecomAgentModelClient.parseResponse(mapper,"{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"成功\"}}]}")
                .path("content").asText()).isEqualTo("成功");
        assertThatThrownBy(()->WecomAgentModelClient.parseResponse(mapper,"{\"error\":\"invalid\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void parsesActualMcpSseResults()throws Exception {
        var result=WecomAgentMcpTransport.parse(mapper,"event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[]}}\n\n");
        assertThat(result.path("result").path("tools").isArray()).isTrue();
    }
}
