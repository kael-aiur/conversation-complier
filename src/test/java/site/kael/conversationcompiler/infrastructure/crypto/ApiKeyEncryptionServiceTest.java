package site.kael.conversationcompiler.infrastructure.crypto;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ApiKeyEncryptionServiceTest {
 @Test void encryptsAndDecryptsWithoutKeepingPlaintext(){var s=new ApiKeyEncryptionService("test-secret");var encrypted=s.encrypt("sk-test-1234");assertThat(encrypted).doesNotContain("sk-test-1234");assertThat(s.decrypt(encrypted)).isEqualTo("sk-test-1234");}
 @Test void requiresSecretWhenEncrypting(){assertThatThrownBy(()->new ApiKeyEncryptionService("").encrypt("key")).isInstanceOf(IllegalStateException.class);}
}
