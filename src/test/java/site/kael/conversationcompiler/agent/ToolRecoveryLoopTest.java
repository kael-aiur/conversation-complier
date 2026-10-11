package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise the real Spring AI tool loop, without an external model or MCP server. */
class ToolRecoveryLoopTest {
    @Test
    void failedToolsAndUnknownNamesReturnMatchedResponsesAndDoNotAbortTheRestOfTheBatch() {
        var model = model();
        var badTool = ToolCallRecoveryTest.tool("okf_search");
        when(badTool.call(anyString(), any())).thenThrow(new IllegalStateException("MCP network failure"));
        var goodTool = ToolCallRecoveryTest.tool("okf_read_concept");
        when(goodTool.call(anyString(), any())).thenReturn("existing page");
        var rounds = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            if (rounds.incrementAndGet() == 1) {
                return toolResponse(List.of(call("bad", "okf_search", "{}"), call("missing", "unavailable_tool", "{}"), call("good", "okf_read_concept", "{}")));
            }
            Prompt prompt = invocation.getArgument(0);
            var result = (ToolResponseMessage) prompt.getInstructions().getLast();
            assertThat(result.getResponses()).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("bad", "missing", "good");
            assertThat(ToolCallRecovery.isFailure(result.getResponses().get(0).responseData())).isTrue();
            assertThat(ToolCallRecovery.isFailure(result.getResponses().get(1).responseData())).isTrue();
            assertThat(result.getResponses().get(2).responseData()).isEqualTo("existing page");
            var assistant = (AssistantMessage) prompt.getInstructions().get(prompt.getInstructions().size() - 2);
            assertThat(assistant.getToolCalls()).hasSize(3);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("model saw the failures"))));
        });
        var observer = new ToolCallRecoveryTest.TraceObserver();
        String result = client(model).prompt().user("compile").tools(List.of(
                ToolCallRecovery.wrap(badTool, observer), ToolCallRecovery.wrap(goodTool, observer))).call().content();
        assertThat(result).isEqualTo("model saw the failures");
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test
    void malformedThenRejectedCompileReportsCanBeRepairedBeforeSuccessfulReturnDirect() throws Exception {
        var model = model();
        var mapper = new ObjectMapper();
        var collector = new CompileResultCollector(mapper);
        collector.begin();
        ToolCallback report = FunctionToolCallback.<CompileResultRequest, String>builder("compile_result", collector::accept)
                .description("report").inputType(CompileResultRequest.class)
                .toolMetadata(ToolMetadata.builder().returnDirect(true).build()).build();
        var round = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = round.incrementAndGet();
            Prompt prompt = invocation.getArgument(0);
            if (current > 1) {
                var results = (ToolResponseMessage) prompt.getInstructions().getLast();
                assertThat(ToolCallRecovery.isFailure(results.getResponses().getFirst().responseData())).isTrue();
                assertThat(collector.get()).isNull();
            }
            String input = switch (current) {
                case 1 -> "{\"summary\":\"unfinished";
                case 2 -> "{\"summary\":\"\",\"items\":[],\"warnings\":[]}";
                case 3 -> "{\"summary\":\"done\",\"items\":[],\"warnings\":[]}";
                default -> throw new AssertionError("valid report should immediately end the tool loop");
            };
            return toolResponse(List.of(call("report-" + current, "compile_result", input)));
        });
        String result = client(model).prompt().user("compile").tools(List.of(
                ToolCallRecovery.wrap(report, new ToolCallRecoveryTest.TraceObserver()))).call().content();
        assertThat(mapper.readTree(result).asText()).isEqualTo("compile_result accepted");
        assertThat(collector.get().summary()).isEqualTo("done");
        verify(model, times(3)).call(any(Prompt.class));
        collector.clear();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void partialWritesSurviveMalformedThirdCallAndTheModelRepairsOnlyThatCall() {
        var model = model();
        var writes = new java.util.ArrayList<String>();
        ToolCallback write = FunctionToolCallback.<java.util.Map, String>builder("okf_write_concept", args -> {
            writes.add((String) args.get("slug"));
            return "updated " + args.get("slug");
        }).description("write").inputType(java.util.Map.class).build();
        var round = new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            int current = round.incrementAndGet();
            if (current == 1) {
                return toolResponse(List.of(call("one", "okf_write_concept", "{\"slug\":\"one\"}"),
                        call("two", "okf_write_concept", "{\"slug\":\"two\"}"),
                        call("three", "okf_write_concept", "{\"slug\":\"three\",\"content\":\"unfinished")));
            }
            Prompt prompt = invocation.getArgument(0);
            var results = (ToolResponseMessage) prompt.getInstructions().getLast();
            if (current == 2) {
                assertThat(writes).containsExactly("one", "two");
                assertThat(results.getResponses()).hasSize(3);
                assertThat(ToolCallRecovery.isFailure(results.getResponses().get(2).responseData())).isTrue();
                return toolResponse(List.of(call("three-fixed", "okf_write_concept", "{\"slug\":\"three\",\"content\":\"complete\"}")));
            }
            assertThat(writes).containsExactly("one", "two", "three");
            assertThat(results.getResponses().getFirst().id()).isEqualTo("three-fixed");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("done"))));
        });
        var observer = new ToolCallRecoveryTest.TraceObserver();
        assertThat(client(model).prompt().user("compile").tools(List.of(ToolCallRecovery.wrap(write, observer))).call().content())
                .isEqualTo("done");
        assertThat(observer.messages).filteredOn(trace -> trace.type().equals("knowledge_write")).hasSize(3);
        assertThat(observer.finished).extracting(ToolCallRecoveryTest.Trace::status)
                .containsExactly("completed", "completed", "failed", "completed");
        verify(model, times(3)).call(any(Prompt.class));
    }

    private ChatModel model() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        return model;
    }
    private ChatClient client(ChatModel model) {
        return ChatClient.builder(model).defaultAdvisors(CompilerChatClientFactory.toolCallingAdvisor()).build();
    }
    private static AssistantMessage.ToolCall call(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }
    private static ChatResponse toolResponse(List<AssistantMessage.ToolCall> calls) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));
    }
}
