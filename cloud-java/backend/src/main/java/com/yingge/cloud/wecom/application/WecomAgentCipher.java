package com.yingge.cloud.wecom.application;

import com.yingge.cloud.shared.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** Dedicated authenticated encryption, independent of Feishu and QQ configuration. */
@Component
public class WecomAgentCipher {
    private static final byte[] AAD = "yingge-wecom-agent-v1".getBytes(StandardCharsets.UTF_8);
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public WecomAgentCipher(@Value("${app.wecom.aibot.agent.master-key:}") String configured) {
        if (configured == null || configured.isBlank()) { key = null; return; }
        try {
            byte[] bytes = Base64.getDecoder().decode(configured.trim());
            if (bytes.length != 32) throw new IllegalArgumentException();
            key = new SecretKeySpec(bytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("WECOM_AGENT_MASTER_KEY must encode exactly 32 bytes");
        }
    }

    public boolean configured() { return key != null; }
    public String encrypt(String value) { return Base64.getEncoder().encodeToString(encryptBytes(value)); }
    public String decrypt(String value) {
        try { return decryptBytes(Base64.getDecoder().decode(value)); }
        catch (IllegalArgumentException exception) { throw invalidCiphertext(); }
    }

    public byte[] encryptBytes(String value) {
        requireKey();
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + nonce.length + encrypted.length).put((byte) 1).put(nonce).put(encrypted).array();
        } catch (Exception exception) {
            throw ApiException.unavailable("WECOM_ENCRYPTION_FAILED", "企业微信数据无法安全保存");
        }
    }

    public String decryptBytes(byte[] value) {
        requireKey();
        try {
            if (value == null || value.length < 29 || value[0] != 1) throw new IllegalArgumentException();
            ByteBuffer buffer = ByteBuffer.wrap(value, 1, value.length - 1);
            byte[] nonce = new byte[12]; buffer.get(nonce);
            byte[] payload = new byte[buffer.remaining()]; buffer.get(payload);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw invalidCiphertext(); }
    }

    private void requireKey() {
        if (!configured()) throw ApiException.unavailable("WECOM_ENCRYPTION_UNAVAILABLE", "管理员尚未配置企业微信 Agent 加密密钥");
    }
    private ApiException invalidCiphertext() {
        return ApiException.unavailable("WECOM_DECRYPTION_FAILED", "企业微信数据无法解密，请联系管理员");
    }
}
