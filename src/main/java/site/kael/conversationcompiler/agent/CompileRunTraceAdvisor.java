package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatResponse;
import site.kael.conversationcompiler.agent.ModelFailoverRunner.AttemptObserver;
import site.kael.conversationcompiler.common.FailureMessageSanitizer;
import site.kael.conversationcompiler.repository.compiler.CompileRunTraceRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Captures each model round and small diagnostics independently of truncated message previews. */
public final class CompileRunTraceAdvisor implements CallAdvisor {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final AttemptObserver observer;
    public CompileRunTraceAdvisor(AttemptObserver observer) { this.observer = observer; }
    AttemptObserver observer() { return observer; }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long pending = observer.traceStarted("model_call", "assistant", "模型请求", "正在等待模型响应…");
        try {
            ChatClientResponse response = chain.nextCall(request);
            if (response.chatResponse() != null) {
                ChatResponse chatResponse = response.chatResponse();
                Map<String, Object> diagnostics = new LinkedHashMap<>();
                var metadata = chatResponse.getMetadata();
                diagnostics.put("responseId", metadata.getId());
                diagnostics.put("model", metadata.getModel());
                var usage = metadata.getUsage();
                diagnostics.put("usageMetadataAvailable", !(usage instanceof org.springframework.ai.chat.metadata.EmptyUsage));
                diagnostics.put("inputTokens", usage.getPromptTokens());
                diagnostics.put("outputTokens", usage.getCompletionTokens());
                diagnostics.put("totalTokens", usage.getTotalTokens());
                diagnostics.put("usageNote", "SDK 映射值；上游未提供时可能为零，不能据此断言没有消耗或截断");
                var options = request.prompt().getOptions();
                Integer outputBudget = options == null ? null : options.getMaxTokens();
                if (options instanceof org.springframework.ai.openai.OpenAiChatOptions openai && openai.getMaxCompletionTokens() != null) {
                    outputBudget = openai.getMaxCompletionTokens();
                }
                diagnostics.put("requestedMaxOutputTokens", outputBudget);
                diagnostics.put("outputBudgetNote", outputBudget == null ? "未显式设置；供应商实际限制未知" : "请求值，供应商实际限制可能不同");
                diagnostics.put("traceContentLimitChars", CompileRunTraceRepository.MAX_CONTENT_CHARS);
                var generations = new ArrayList<Map<String, Object>>();
                for (Generation generation : chatResponse.getResults()) {
                    var generationDiagnostics = new LinkedHashMap<String, Object>();
                    generationDiagnostics.put("finishReason", generation.getMetadata().getFinishReason());
                    AssistantMessage output = generation.getOutput();
                    generationDiagnostics.put("assistantContentChars", output == null || output.getText() == null ? 0 : output.getText().length());
                    var tools = new ArrayList<Map<String, Object>>();
                    if (output != null && output.getText() != null && !output.getText().isBlank()) {
                        observer.trace("message", "assistant", "模型回复", output.getText());
                    }
                    if (output != null && output.getToolCalls() != null) {
                        output.getToolCalls().forEach(tool -> {
                            observer.trace("tool_request", "assistant", "请求调用工具：" + tool.name(), tool.arguments());
                            var detail = new LinkedHashMap<String, Object>();
                            detail.put("id", tool.id());
                            detail.put("name", tool.name());
                            String arguments = tool.arguments() == null ? "" : tool.arguments();
                            detail.put("argumentChars", arguments.length());
                            detail.put("tracePreviewTruncated", FailureMessageSanitizer.sanitize(arguments).length() > CompileRunTraceRepository.MAX_CONTENT_CHARS);
                            try { var parsed = JSON.readTree(arguments); detail.put("argumentsValidJsonObject", parsed != null && parsed.isObject()); }
                            catch (Exception ignored) { detail.put("argumentsValidJsonObject", false); }
                            tools.add(detail);
                        });
                    }
                    generationDiagnostics.put("toolCalls", tools);
                    generations.add(generationDiagnostics);
                }
                diagnostics.put("generations", generations);
                observer.trace("model_diagnostics", "system", "模型响应诊断（结束原因 / Token / 工具参数）", ToolCallRecovery.json(diagnostics));
            }
            observer.traceFinished(pending, "completed", "模型已返回响应");
            return response;
        } catch (RuntimeException error) {
            observer.traceFinished(pending, "failed", site.kael.conversationcompiler.common.FailureDetails.describe(error));
            throw error;
        }
    }

    @Override public String getName() { return "compile-run-trace"; }
    @Override public int getOrder() { return ToolCallingAdvisor.DEFAULT_ORDER + 1; }
}
