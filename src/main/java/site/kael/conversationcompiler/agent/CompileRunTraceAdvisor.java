package site.kael.conversationcompiler.agent;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import site.kael.conversationcompiler.agent.ModelFailoverRunner.AttemptObserver;

/** Captures each model request/response round while nested inside Spring AI's tool loop. */
public final class CompileRunTraceAdvisor implements CallAdvisor {
    private final AttemptObserver observer;
    public CompileRunTraceAdvisor(AttemptObserver observer) { this.observer = observer; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long pending = observer.traceStarted("model_call", "assistant", "模型请求", "正在等待模型响应…");
        try {
            ChatClientResponse response = chain.nextCall(request);
            if (response.chatResponse() != null) {
                ChatResponse chatResponse = response.chatResponse();
                for (Generation generation : chatResponse.getResults()) {
                    AssistantMessage output = generation.getOutput();
                    if (output != null && output.getText() != null && !output.getText().isBlank()) {
                        observer.trace("message", "assistant", "模型回复", output.getText());
                    }
                    if (output != null && output.getToolCalls() != null) {
                        output.getToolCalls().forEach(tool -> observer.trace("tool_request", "assistant",
                                "请求调用工具：" + tool.name(), tool.arguments()));
                    }
                }
            }
            observer.traceFinished(pending, "completed", "模型已返回响应");
            return response;
        } catch (RuntimeException error) {
            observer.traceFinished(pending, "failed", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        }
    }

    @Override public String getName() { return "compile-run-trace"; }
    @Override public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER + 1; }
}
