package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.mcp.oauth.McpOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WecomNaturalLanguageServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final WecomAgentCipher cipher=mock(WecomAgentCipher.class);
    private final WecomMcpAuthorizationService authorization=mock(WecomMcpAuthorizationService.class);
    private final WecomAgentMcpTransport transport=mock(WecomAgentMcpTransport.class);
    private final WecomAgentMcpTransport.Connection session=mock(WecomAgentMcpTransport.Connection.class);
    private final WecomAgentModelClient model=mock(WecomAgentModelClient.class);
    private final WecomPendingActionService pending=mock(WecomPendingActionService.class);
    private final UUID org=UUID.randomUUID(),member=UUID.randomUUID();
    private final WecomNaturalLanguageService service=new WecomNaturalLanguageService(jdbc,mapper,cipher,authorization,transport,model,pending);

    private void setup() {
        when(authorization.requireAuthorization(org,member)).thenReturn(new McpOAuthService.TokenAuthentication(
                UUID.randomUUID(),UUID.randomUUID(),McpOAuthService.WECOM_AGENT_CLIENT_ID,UUID.randomUUID(),member,org,
                Set.of("yingge.read","yingge.write"),0,0));
        when(authorization.accessToken(org,member)).thenReturn("synthetic-token");
        when(transport.open(eq("synthetic-token"),any(Duration.class))).thenReturn(session);
        when(model.configured()).thenReturn(true);
        when(cipher.encrypt(anyString())).thenReturn("encrypted-only");
    }
    private JsonNode call(String name,String arguments)throws Exception {
        return mapper.readTree("{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"id\":\"call-"+name+"\",\"type\":\"function\",\"function\":{\"name\":\""+name+"\",\"arguments\":"+mapper.writeValueAsString(arguments)+"}}]}");
    }

    @Test void discoveredWritesUseAuthenticatedMcpAndServerGeneratedStableIdempotency()throws Exception {
        setup();
        var tool=mapper.readTree("""
                {"name":"yingge_interaction_create","description":"沟通","_meta":{"requiredScope":"yingge.write"},
                 "annotations":{"readOnlyHint":false},"inputSchema":{"type":"object","properties":{
                 "customerId":{"type":"string"},"request":{"type":"object"},"idempotencyKey":{"type":"string"}}}}
                """);
        when(session.request(eq("tools/list"),any(),any())).thenReturn(mapper.valueToTree(Map.of("tools",List.of(tool))));
        when(session.request(eq("tools/call"),any(),any())).thenReturn(mapper.readTree("{\"isError\":false,\"structuredContent\":{\"status\":\"SUCCESS\"}}"));
        when(model.complete(anyList(),anyList(),any())).thenReturn(call("discover_tools","{\"query\":\"沟通\"}"),
                call("yingge_interaction_create","{\"customerId\":\"known\",\"request\":{\"content\":\"真实沟通\"}}"),
                mapper.readTree("{\"role\":\"assistant\",\"content\":\"已记录\"}"));
        assertThat(service.respond(org,member,"chat","记录一次真实沟通","event-1",command->"当前客户known"))
                .isEqualTo("已记录");
        verify(authorization).requireScope(org,member,"yingge.write");
        verify(session).request(eq("tools/call"),argThat(raw->{
            var args=(JsonNode)((Map<?,?>)raw).get("arguments");
            return args.path("idempotencyKey").asText().startsWith("wecom-")&&!args.has("approvalId");
        }),any());
        verify(session).close();
    }

    @Test void modelCannotConfirmAndDeterministicPreviewCannotBeRewritten()throws Exception {
        setup();when(session.request(eq("tools/list"),any(),any())).thenReturn(mapper.readTree("{\"tools\":[]}"));
        var invoked=new java.util.ArrayList<String>();
        when(model.complete(anyList(),anyList(),any())).thenReturn(
                call("shared_workflow","{\"command\":\"/confirm\"}"),
                call("shared_workflow","{\"command\":\"/set bonus=600\"}"),
                mapper.readTree("{\"role\":\"assistant\",\"content\":\"已成单！金额是9999\"}"));
        String preview="奖金600；请本人确认 [[WECOM_CONFIRM:real-token]]";
        String result=service.respond(org,member,"chat","奖金600","event-2",command->{
            invoked.add(command);return command.equals("/current")?"当前客户":preview;
        });
        assertThat(result).isEqualTo(preview).doesNotContain("9999");
        assertThat(invoked).containsExactly("/current","/set bonus=600");
        verify(session,never()).request(eq("tools/call"),any(),any());
    }
}
