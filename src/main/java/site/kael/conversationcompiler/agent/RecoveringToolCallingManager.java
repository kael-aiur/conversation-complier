package site.kael.conversationcompiler.agent;

import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

/** Keeps framework history/limits intact, while failed terminal tools still reach the model. */
final class RecoveringToolCallingManager implements ToolCallingManager {
    private final ToolCallingManager delegate;

    RecoveringToolCallingManager(ModelFailoverRunner.AttemptObserver observer) {
        // Fallback resolves ONLY synthetic failing callbacks, never additional MCP tools.
        delegate = ToolCallingManager.builder().resolutionFallbackEnabled(true).toolCallbackResolver(name -> ToolCallRecovery.wrap(new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("Unavailable tool").inputSchema("{}").build();
            }
            @Override public String call(String arguments) {
                throw new IllegalArgumentException("Unknown or unavailable tool. Use only the tools exposed in this request.");
            }
        }, observer)).build();
    }

    @Override public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        ToolExecutionResult result = delegate.executeToolCalls(prompt, response);
        if (result.returnDirect() && result.conversationHistory().getLast() instanceof ToolResponseMessage tools
                && tools.getResponses().stream().anyMatch(tool -> ToolCallRecovery.isFailure(tool.responseData()))) {
            // compile_result has returnDirect=true, but malformed/rejected reports must be repaired.
            return ToolExecutionResult.builder().conversationHistory(result.conversationHistory()).returnDirect(false).build();
        }
        return result;
    }
}
