package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CompileRunTraceAdvisorTest {
    @Test
    void recordsFinishReasonUsageOutputBudgetAndActualArgumentLengthWithoutMutatingTheResponse() throws Exception {
        var observer = new ToolCallRecoveryTest.TraceObserver();
        var advisor = new CompileRunTraceAdvisor(observer);
        var chain = mock(CallAdvisorChain.class);
        String longArguments = "{\"content\":\"" + "a".repeat(20000) + "\"}";
        String incomplete = "{\"content\":\"unfinished";
        var message = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("long", "function", "okf_write_concept", longArguments),
                new AssistantMessage.ToolCall("bad", "function", "okf_write_concept", incomplete))).build();
        var usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(100);
        when(usage.getCompletionTokens()).thenReturn(200);
        when(usage.getTotalTokens()).thenReturn(300);
        var response = ChatClientResponse.builder().chatResponse(new ChatResponse(List.of(
                new Generation(message, ChatGenerationMetadata.builder().finishReason("length").build())),
                ChatResponseMetadata.builder().model("model").id("response").usage(usage).build())).build();
        var request = ChatClientRequest.builder().prompt(new Prompt("input", OpenAiChatOptions.builder().maxCompletionTokens(1234).build())).build();
        when(chain.nextCall(request)).thenReturn(response);
        assertThat(advisor.adviseCall(request, chain)).isSameAs(response);
        var trace = observer.messages.stream().filter(t -> t.type().equals("model_diagnostics")).findFirst().orElseThrow();
        var data = new ObjectMapper().readTree(trace.content());
        assertThat(data.path("usageMetadataAvailable").asBoolean()).isTrue();
        assertThat(data.path("inputTokens").asInt()).isEqualTo(100);
        assertThat(data.path("outputTokens").asInt()).isEqualTo(200);
        assertThat(data.path("totalTokens").asInt()).isEqualTo(300);
        assertThat(data.path("requestedMaxOutputTokens").asInt()).isEqualTo(1234);
        var generation = data.path("generations").get(0);
        assertThat(generation.path("finishReason").asText()).isEqualTo("length");
        var tools = generation.path("toolCalls");
        assertThat(tools.get(0).path("argumentChars").asInt()).isEqualTo(longArguments.length());
        assertThat(tools.get(0).path("tracePreviewTruncated").asBoolean()).isTrue();
        assertThat(tools.get(0).path("argumentsValidJsonObject").asBoolean()).isTrue();
        assertThat(tools.get(1).path("argumentChars").asInt()).isEqualTo(incomplete.length());
        assertThat(tools.get(1).path("tracePreviewTruncated").asBoolean()).isFalse();
        assertThat(tools.get(1).path("argumentsValidJsonObject").asBoolean()).isFalse();
        assertThat(observer.messages).anySatisfy(t -> assertThat(t.content()).isEqualTo(longArguments));
    }

    @Test
    void missingUsageAndOutputLimitsAreExplicitlyUnknown() throws Exception {
        var observer = new ToolCallRecoveryTest.TraceObserver();
        var chain = mock(CallAdvisorChain.class);
        var response = ChatClientResponse.builder().chatResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("done"))))).build();
        var request = ChatClientRequest.builder().prompt(new Prompt("input")).build();
        when(chain.nextCall(request)).thenReturn(response);
        new CompileRunTraceAdvisor(observer).adviseCall(request, chain);
        var trace = observer.messages.stream().filter(t -> t.type().equals("model_diagnostics")).findFirst().orElseThrow();
        var data = new ObjectMapper().readTree(trace.content());
        assertThat(data.path("usageMetadataAvailable").asBoolean()).isFalse();
        assertThat(data.path("requestedMaxOutputTokens").isNull()).isTrue();
        assertThat(data.path("outputBudgetNote").asText()).contains("未知");
    }
}
