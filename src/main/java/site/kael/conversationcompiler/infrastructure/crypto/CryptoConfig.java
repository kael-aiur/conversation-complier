package site.kael.conversationcompiler.infrastructure.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {
    @Bean public ApiKeyEncryptionService apiKeyEncryptionService(@Value("${conversation-compiler.security.secret-key:}") String secret) { return new ApiKeyEncryptionService(secret); }
}
