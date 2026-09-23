package site.kael.conversationcompiler.infrastructure.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class ApiKeyEncryptionService {
    private final byte[] key;
    private final boolean configured;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyEncryptionService(String secret) {
        this.configured = secret != null && !secret.isBlank();
        try { this.key = MessageDigest.getInstance("SHA-256").digest((secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public String encrypt(String value) {
        if (!configured) throw new CryptoConfigurationException("provider credential encryption is not configured");
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) { throw new IllegalStateException("Unable to encrypt API key", e); }
    }
    public String decrypt(String value) {
        if (!configured) throw new CryptoConfigurationException("provider credential encryption is not configured");
        try {
            byte[] all = Base64.getDecoder().decode(value); byte[] iv = java.util.Arrays.copyOfRange(all, 0, 12); byte[] encrypted = java.util.Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to decrypt API key", e); }
    }
    public String fingerprint(String value) { try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12); } catch (Exception e) { throw new IllegalStateException(e); } }
    public String mask(String value) { return value.length() <= 4 ? "••••" : "••••••••" + value.substring(value.length() - 4); }
}
