package site.kael.conversationcompiler.agent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class CompilerChatClientFactoryTest {
    @Test
    void keepsAssistantToolCallsAndToolResultsTogetherAcrossModelRounds() {
        var advisor = CompilerChatClientFactory.toolCallingAdvisor();
        assertThat(ReflectionTestUtils.getField(advisor, "conversationHistoryEnabled")).isEqualTo(true);
    }
}
