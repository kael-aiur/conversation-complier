package site.kael.conversationcompiler.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import com.anthropic.models.messages.Model;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import site.kael.conversationcompiler.common.BadRequestException;
import site.kael.conversationcompiler.domain.settings.InterfaceType;
import site.kael.conversationcompiler.infrastructure.crypto.ApiKeyEncryptionService;
import site.kael.conversationcompiler.repository.settings.ModelProviderRepository;

@Component
public class CompilerChatClientFactory {
    private final ModelProviderRepository providers;
    private final ApiKeyEncryptionService crypto;
    public CompilerChatClientFactory(ModelProviderRepository providers, ApiKeyEncryptionService crypto) { this.providers = providers; this.crypto = crypto; }

    public ChatClient create(String providerId, String modelName) {
        return create(providerId, modelName, null);
    }

    public ChatClient create(String providerId, String modelName, CallAdvisor traceAdvisor) {
        var provider = providers.findById(providerId).orElseThrow(() -> new BadRequestException("provider not found: " + providerId));
        if (!provider.enabled()) throw new BadRequestException("provider is disabled");
        String apiKey = crypto.decrypt(providers.findEncryptedApiKey(providerId));
        org.springframework.ai.chat.model.ChatModel model;
        if (provider.interfaceType() == InterfaceType.anthropic) {
            var options = AnthropicChatOptions.builder().baseUrl(provider.baseUrl()).apiKey(apiKey).model(Model.of(modelName)).timeout(Duration.ofSeconds(45)).maxRetries(0).proxy(proxy()).build();
            model = AnthropicChatModel.builder().options(options).build();
        } else {
            var options = OpenAiChatOptions.builder().baseUrl(provider.baseUrl()).apiKey(apiKey).model(modelName).timeout(Duration.ofSeconds(45)).maxRetries(0).proxy(proxy()).build();
            model = OpenAiChatModel.builder().options(options).build();
        }
        // Keep the full tool-call exchange for the duration of this single agent run.
        // OpenAI-compatible APIs require each tool result to follow its assistant tool_calls message.
        var builder = ChatClient.builder(model).defaultAdvisors(toolCallingAdvisor());
        if (traceAdvisor != null) builder.defaultAdvisors(traceAdvisor);
        return builder.build();
    }
    static ToolCallingAdvisor toolCallingAdvisor() {
        return ToolCallingAdvisor.builder().conversationHistoryEnabled(true).build();
    }

    private Proxy proxy() {
        String value = System.getenv("CONVERSATION_COMPILER_HTTP_PROXY");
        if (value == null || value.isBlank()) value = System.getenv("HTTPS_PROXY");
        if (value == null || value.isBlank()) return Proxy.NO_PROXY;
        try { URI uri = URI.create(value); return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 80)); }
        catch (Exception ignored) { return Proxy.NO_PROXY; }
    }
}
