package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import site.kael.conversationcompiler.domain.ConversationEvent;
import site.kael.conversationcompiler.repository.settings.ModelProviderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@ConditionalOnProperty(name = "conversation-compiler.agent.enabled", havingValue = "true")
public class CompilerAgentService {
    private final CompilerChatClientFactory chatClientFactory;
    private final ObjectMapper mapper;
    private final CompileResultCollector collector;
    private final ObjectProvider<ToolCallbackProvider> mcpProviders;
    private final ModelProviderRepository providers;
    private final ModelFailoverRunner failover = new ModelFailoverRunner();

    public CompilerAgentService(CompilerChatClientFactory chatClientFactory, ObjectMapper mapper,
                                CompileResultCollector collector, ObjectProvider<ToolCallbackProvider> mcpProviders, ModelProviderRepository providers) {
        this.chatClientFactory = chatClientFactory;
        this.mapper = mapper; this.collector = collector; this.mcpProviders = mcpProviders; this.providers = providers;
    }

    public CompileResultRequest compile(String requirements, String providerId, String modelName, String sessionId, long fromVersion, long toVersion, List<ConversationEvent> events) { return compile(requirements, providerId, modelName, sessionId, fromVersion, toVersion, events, new ModelFailoverRunner.AttemptObserver(){public void started(String c,int a){} public void finished(String c,int a,String s,Throwable e){}}); }

    public CompileResultRequest compile(String requirements, String providerId, String modelName, String sessionId, long fromVersion, long toVersion, List<ConversationEvent> events, ModelFailoverRunner.AttemptObserver observer) {
        List<String> candidates = new ArrayList<>();
        candidates.add(providerId + "\n" + modelName);
        providers.findAll().stream().filter(p -> p.enabled()).flatMap(p -> p.models().stream().map(m -> p.id() + "\n" + m)).forEach(c -> { if (!candidates.contains(c)) candidates.add(c); });
        return failover.run(candidates, candidate -> {
            String[] parts = candidate.split("\n", 2);
            return runOnce(requirements, parts[0], parts[1], sessionId, fromVersion, toVersion, events);
        }, observer);
    }

    private CompileResultRequest runOnce(String requirements, String providerId, String modelName, String sessionId, long fromVersion, long toVersion, List<ConversationEvent> events) {
        collector.begin();
        try {
            ChatClient chatClient = chatClientFactory.create(providerId, modelName);
            List<ToolCallback> tools = new ArrayList<>();
            mcpProviders.orderedStream().forEach(provider -> java.util.Collections.addAll(tools, provider.getToolCallbacks()));
            tools.add(compileResultTool());
            String system = """
                    你是 Conversation Compiler 的知识整理 Agent。
                    只提取有明确证据支持的稳定事实、项目决策、用户偏好、可复用流程和实体关系。
                    忽略普通问答、临时调试、助手推测以及密码、Token、API Key 和私钥。
                    先使用知识库搜索工具检查已有知识，再通过 LLMWikiNG OKF MCP 创建或更新知识。
                    知识写入完成后，必须调用 compile_result 工具报告总结和知识条目元数据。
                    如果没有调用 compile_result，本次整理不能视为成功。

                    用户配置的整理要求：
                    --- BEGIN REQUIREMENTS ---
                    %s
                    --- END REQUIREMENTS ---
                    """.formatted(requirements == null ? "" : requirements);
            String user = mapper.writeValueAsString(new AgentInput(sessionId, fromVersion, toVersion, events));
            chatClient.prompt().system(system).user(user).toolCallbacks(tools).call().content();
            CompileResultRequest result = collector.get();
            if (result == null) throw new IllegalStateException("compiler agent did not call compile_result");
            return result;
        } catch (Exception e) { String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); throw new IllegalStateException("compiler agent failed: " + detail, e); }
        finally { collector.clear(); }
    }

    private ToolCallback compileResultTool() {
        return FunctionToolCallback.<CompileResultRequest, String>builder("compile_result", collector::accept)
                .description("报告本次知识整理的总结和知识条目元数据。")
                .inputType(CompileResultRequest.class)
                .build();
    }

    private record AgentInput(String sessionId, long fromVersion, long toVersion, List<ConversationEvent> events) {}
}
