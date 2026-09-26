package site.kael.conversationcompiler.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FailureMessageSanitizerTest {
    @Test
    void redactsAuthenticationHeadersAndBearerValues() {
        String safe = FailureMessageSanitizer.sanitize(
                "HTTP 401 Authorization: Bearer bearer-secret X-API-Key: api-secret CF-Access-Client-Secret=access-secret");
        assertThat(safe).contains("Bearer [redacted]", "X-API-Key: [redacted]", "CF-Access-Client-Secret=[redacted]");
        assertThat(safe).doesNotContain("bearer-secret", "api-secret", "access-secret");
    }

    @Test
    void redactsCredentialFieldsInJsonMessages() {
        String safe = FailureMessageSanitizer.sanitize("{\"apiKey\":\"api-secret\",\"client_secret\":\"client-secret\"}");
        assertThat(safe).contains("[redacted]").doesNotContain("api-secret", "client-secret");
    }

    @Test
    void redactsCredentialsEmbeddedInUrl() {
        assertThat(FailureMessageSanitizer.sanitize("failed https://user:password@example.invalid/v1"))
                .isEqualTo("failed https://[redacted]@example.invalid/v1");
    }
}
