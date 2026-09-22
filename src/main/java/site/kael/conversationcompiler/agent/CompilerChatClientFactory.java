package site.kael.conversationcompiler.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import com.anthropic.models.messages.Model;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
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
        var provider = providers.findById(providerId).orElseThrow(() -> new BadRequestException("provider not found: " + providerId));
        if (!provider.enabled()) throw new BadRequestException("provider is disabled");
        String apiKey = crypto.decrypt(providers.findEncryptedApiKey(providerId));
        org.springframework.ai.chat.model.ChatModel model;
        if (provider.interfaceType() == InterfaceType.anthropic) {
            var options = AnthropicChatOptions.builder().baseUrl(provider.baseUrl()).apiKey(apiKey).model(Model.of(modelName)).build();
            model = AnthropicChatModel.builder().options(options).build();
        } else {
            var options = OpenAiChatOptions.builder().baseUrl(provider.baseUrl()).apiKey(apiKey).model(modelName).build();
            model = OpenAiChatModel.builder().options(options).build();
        }
        return ChatClient.builder(model).defaultAdvisors(ToolCallingAdvisor.builder().disableInternalConversationHistory().build()).build();
    }
}
