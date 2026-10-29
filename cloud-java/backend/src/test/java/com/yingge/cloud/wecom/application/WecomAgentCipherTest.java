package com.yingge.cloud.wecom.application;

import com.yingge.cloud.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class WecomAgentCipherTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);
    @Test void randomizedAuthenticatedEncryptionRoundTripsWithoutPlaintext() {
        var cipher=new WecomAgentCipher(key);
        String first=cipher.encrypt("合成测试客户");
        String second=cipher.encrypt("合成测试客户");
        assertThat(first).isNotEqualTo(second).doesNotContain("合成");
        assertThat(cipher.decrypt(first)).isEqualTo("合成测试客户");
        byte[] bytes=Base64.getDecoder().decode(first); bytes[bytes.length-1]^=1;
        assertThatThrownBy(()->cipher.decryptBytes(bytes)).isInstanceOf(ApiException.class);
    }
    @Test void absentKeyFailsClosedAndInvalidConfigurationIsRejected() {
        var cipher=new WecomAgentCipher("");
        assertThat(cipher.configured()).isFalse();
        assertThatThrownBy(()->cipher.encrypt("test")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->new WecomAgentCipher("not-base64")).isInstanceOf(IllegalStateException.class);
    }
}
