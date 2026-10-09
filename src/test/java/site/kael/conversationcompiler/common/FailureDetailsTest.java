package site.kael.conversationcompiler.common;

import org.junit.jupiter.api.Test;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;

class FailureDetailsTest {
    @Test void preservesUnderlyingTimeoutInsteadOfOnlyGenericRequestFailure() {
        Throwable e=new IllegalStateException("Request failed",new SocketTimeoutException("timeout"));
        assertThat(FailureDetails.describe(e)).contains("IllegalStateException: Request failed", "SocketTimeoutException: timeout");
    }
    @Test void redactsCredentialsInNestedMessages() {
        Throwable e=new IllegalStateException("Request failed",new IllegalArgumentException("Authorization: Bearer private-token apiKey=private-key"));
        assertThat(FailureDetails.describe(e)).contains("[redacted]").doesNotContain("private-token","private-key");
    }
    @Test void handlesMessageLessExceptionsAndBoundsLongDetails() {
        assertThat(FailureDetails.describe(new IllegalStateException())).isEqualTo("IllegalStateException");
        assertThat(FailureDetails.describe(new IllegalStateException("x".repeat(10000)))).hasSizeLessThanOrEqualTo(6001);
    }
}
