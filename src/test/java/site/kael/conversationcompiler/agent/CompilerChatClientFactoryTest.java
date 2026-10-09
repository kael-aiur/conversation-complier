package site.kael.conversationcompiler.agent;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class CompilerChatClientFactoryTest {
    @Test
    void modelRequestTimeoutDefaultsTo600SecondsAndSupportsConfiguration() {
        var factory = new CompilerChatClientFactory(
                org.mockito.Mockito.mock(site.kael.conversationcompiler.repository.settings.ModelProviderRepository.class),
                org.mockito.Mockito.mock(site.kael.conversationcompiler.infrastructure.crypto.ApiKeyEncryptionService.class));
        assertThat(factory.modelRequestTimeout()).isEqualTo(java.time.Duration.ofSeconds(600));
        ReflectionTestUtils.setField(factory, "modelRequestTimeoutSeconds", 180L);
        assertThat(factory.modelRequestTimeout()).isEqualTo(java.time.Duration.ofSeconds(180));
    }

    @Test
    void keepsAssistantToolCallsAndToolResultsTogetherAcrossModelRounds() {
        var advisor = CompilerChatClientFactory.toolCallingAdvisor();
        assertThat(ReflectionTestUtils.getField(advisor, "conversationHistoryEnabled")).isEqualTo(true);
    }
}
