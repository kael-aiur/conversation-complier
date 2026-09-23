package site.kael.conversationcompiler;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=",
        "spring.ai.anthropic.api-key="
})
class ConversationCompilerApplicationTests {

    @Test
    void contextLoadsWithoutGlobalLlmCredentials() {
    }
}
