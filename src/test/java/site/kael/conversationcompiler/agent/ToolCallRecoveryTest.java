package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallRecoveryTest {
    private final ObjectMapper json = new ObjectMapper();

    static Stream<RuntimeException> errors() {
        return Stream.of(new IllegalArgumentException("invalid parameters"),
                new IllegalStateException("malformed JSON content string"),
                new IllegalStateException("HTTP 403 permission denied"),
                new IllegalStateException("HTTP 429 rate limited"),
                new IllegalStateException("MCP disconnected"),
                new RuntimeException("request timed out", new SocketTimeoutException("read timeout")),
                new IllegalStateException("page changed; conflict"));
    }

    @ParameterizedTest @MethodSource("errors")
    void returnsAllOrdinaryToolExceptionsAsStructuredModelVisibleResults(RuntimeException error) throws Exception {
        var delegate = tool("okf_write_concept");
        when(delegate.call("{}" )).thenThrow(error);
        var observer = new TraceObserver();
        var wrapped = ToolCallRecovery.wrap(delegate, observer);
        var result = json.readTree(wrapped.call("{}"));
        assertThat(result.path("toolCallFailed").asBoolean()).isTrue();
        assertThat(result.path("tool").asText()).isEqualTo("okf_write_concept");
        assertThat(result.path("message").asText()).contains(error.getMessage());
        assertThat(result.path("sideEffects").asText()).isEqualTo("unknown");
        assertThat(result.path("guidance").asText()).contains("核对", "无限重试");
        assertThat(observer.finished).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo("failed");
            assertThat(trace.content()).startsWith("工具调用失败（已返回给模型）");
        });
        assertThat(observer.messages).isEmpty();
        verify(delegate, times(1)).call("{}"); // The model, not the wrapper, decides whether to retry.
    }

    @Test
    void preservesContextAndAllowsTheModelToCorrectTheNextCall() {
        var delegate = tool("okf_search");
        var context = new ToolContext(Map.of("run", 42));
        when(delegate.call("bad", context)).thenThrow(new IllegalArgumentException("query is required"));
        when(delegate.call("corrected", context)).thenReturn("found");
        var observer = new TraceObserver();
        var wrapped = ToolCallRecovery.wrap(delegate, observer);
        assertThat(ToolCallRecovery.isFailure(wrapped.call("bad", context))).isTrue();
        assertThat(wrapped.call("corrected", context)).isEqualTo("found");
        assertThat(observer.finished).extracting(Trace::status).containsExactly("failed", "completed");
        verify(delegate).call("bad", context);
        verify(delegate).call("corrected", context);
        assertThat(wrapped.getToolDefinition()).isSameAs(delegate.getToolDefinition());
        assertThat(wrapped.getToolMetadata()).isSameAs(delegate.getToolMetadata());
    }

    @Test
    void redactsErrorSecretsBeforeReturningThemToTheModelOrTracingThem() {
        var delegate = tool("okf_search");
        when(delegate.call("{}")).thenThrow(new IllegalStateException("Authorization: Bearer fake-token x-mcp-key: fake-key https://user:fake-password@example.invalid"));
        var observer = new TraceObserver();
        String result = ToolCallRecovery.wrap(delegate, observer).call("{}");
        assertThat(result).contains("[redacted]").doesNotContain("fake-token", "fake-key", "fake-password");
        assertThat(observer.finished.getFirst().content()).doesNotContain("fake-token", "fake-key", "fake-password");
    }

    @Test
    void recordsWriteResponsesIndependentlyOfLongArgumentsWithoutInventingKnowledgeItems() throws Exception {
        var delegate = tool("okf_write_concept");
        String arguments = json.writeValueAsString(Map.of("wiki", "project", "slug", "page", "content", "a".repeat(20000)));
        when(delegate.call(arguments)).thenReturn("server acknowledged update");
        var observer = new TraceObserver();
        assertThat(ToolCallRecovery.wrap(delegate, observer).call(arguments)).isEqualTo("server acknowledged update");
        verify(delegate).call(arguments);
        assertThat(observer.messages).singleElement().satisfies(trace -> {
            assertThat(trace.type()).isEqualTo("knowledge_write");
            assertThat(trace.content()).contains("tool_returned", "project", "page", "server acknowledged update");
            assertThat(trace.content().length()).isLessThan(12000);
        });
    }

    @Test
    void rejectedCompileReportIsAToolFailureRatherThanSuccess() {
        var delegate = tool("compile_result");
        when(delegate.call("{}")).thenReturn("compile_result rejected: summary is required");
        var observer = new TraceObserver();
        assertThat(ToolCallRecovery.isFailure(ToolCallRecovery.wrap(delegate, observer).call("{}"))).isTrue();
        assertThat(observer.finished.getFirst().status()).isEqualTo("failed");
    }

    @Test
    void cancellationDoesNotBecomeARecoverableToolResponse() {
        var delegate = tool("okf_write_concept");
        when(delegate.call("{}")).thenThrow(new IllegalStateException("interrupted", new InterruptedException()));
        assertThatThrownBy(() -> ToolCallRecovery.wrap(delegate, new TraceObserver()).call("{}"))
                .isInstanceOf(CancellationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void alreadyInterruptedTasksDoNotInvokeTools() {
        var delegate = tool("okf_write_concept");
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> ToolCallRecovery.wrap(delegate, new TraceObserver()).call("{}"))
                .isInstanceOf(CancellationException.class);
        verify(delegate, never()).call(anyString());
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        var delegate = tool("okf_search");
        when(delegate.call("{}")).thenThrow(new AssertionError("fatal"));
        assertThatThrownBy(() -> ToolCallRecovery.wrap(delegate, new TraceObserver()).call("{}"))
                .isInstanceOf(AssertionError.class);
    }

    @AfterEach void clearInterruptFlag() { Thread.interrupted(); }

    static ToolCallback tool(String name) {
        var tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(ToolDefinition.builder().name(name).description(name).inputSchema("{}").build());
        when(tool.getToolMetadata()).thenReturn(ToolMetadata.builder().build());
        return tool;
    }

    record Trace(String type, String status, String content) { }
    static class TraceObserver implements ModelFailoverRunner.AttemptObserver {
        final List<Trace> messages = new ArrayList<>();
        final List<Trace> finished = new ArrayList<>();
        public void started(String candidate, int attempt) { }
        public void finished(String candidate, int attempt, String status, Throwable error) { }
        public long traceStarted(String type, String role, String title, String content) { return 1L; }
        public void trace(String type, String role, String title, String content) { messages.add(new Trace(type, "completed", content)); }
        public void traceFinished(long id, String status, String content) { finished.add(new Trace("tool_execution", status, content)); }
    }
}
