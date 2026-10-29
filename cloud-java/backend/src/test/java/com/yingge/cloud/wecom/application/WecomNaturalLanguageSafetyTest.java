package com.yingge.cloud.wecom.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingge.cloud.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WecomNaturalLanguageSafetyTest {
    private final ObjectMapper mapper=new ObjectMapper();
    @Test void rejectsNestedApprovalCredentialsAndOrganizationSpoofing()throws Exception {
        for(String json:new String[]{"{\"approvalId\":\"fake\"}","{\"request\":{\"organizationId\":\"fake\"}}",
                "{\"x\":[{\"accessToken\":\"secret\"}]}"}) {
            var args=mapper.readTree(json);
            assertThatThrownBy(()->WecomNaturalLanguageService.rejectForbidden(args)).isInstanceOf(ApiException.class);
        }
        WecomNaturalLanguageService.rejectForbidden(mapper.readTree("{\"customerId\":\"known\",\"ownerMembershipId\":\"known\"}"));
    }
    @Test void schemaPreventsUniversalProxyFields()throws Exception {
        var tool=mapper.readTree("{\"inputSchema\":{\"properties\":{\"customerId\":{\"type\":\"string\"}}}}");
        assertThatThrownBy(()->WecomNaturalLanguageService.validateArguments(tool,mapper.readTree("{\"url\":\"http://evil\"}")))
                .isInstanceOf(ApiException.class);
        assertThat(WecomNaturalLanguageService.validateArguments(tool,mapper.readTree("{\"customerId\":\"known\"}"))
                .path("customerId").asText()).isEqualTo("known");
    }
    @Test void retriesUseAStableEventExecutionKey()throws Exception {
        var one=mapper.readTree("{\"request\":{\"name\":\"test\"}}");
        String key=WecomNaturalLanguageService.stableKey("message-1",1,"create",one);
        assertThat(key).isEqualTo(WecomNaturalLanguageService.stableKey("message-1",1,"create",one));
        assertThat(key).isNotEqualTo(WecomNaturalLanguageService.stableKey("message-2",1,"create",one));
        // Same event slot cannot silently change payload and receive a fresh idempotency key.
        assertThat(key).isEqualTo(WecomNaturalLanguageService.stableKey("message-1",1,"create",mapper.readTree("{}")));
    }
    @Test void modelAuthoredMarkersCannotProduceAnActionableCard() {
        assertThat(WecomNaturalLanguageService.stripConfirmationMarkers("完成 [[WECOM_CONFIRM:invented-token]]"))
                .isEqualTo("完成 ");
    }
    @Test void writeReferencesMustComeFromUserOrPreviouslyReturnedBusinessData()throws Exception {
        String id=java.util.UUID.randomUUID().toString();
        var args=mapper.readTree("{\"customerId\":\""+id+"\"}");
        assertThatThrownBy(()->WecomNaturalLanguageService.ensureKnownReferences(args,java.util.Set.of()))
                .isInstanceOf(ApiException.class);
        WecomNaturalLanguageService.ensureKnownReferences(args,java.util.Set.of(id));
    }
}
