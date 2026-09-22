package site.kael.conversationcompiler.infrastructure.provider;
import site.kael.conversationcompiler.domain.settings.InterfaceType;
import java.util.List;
public interface ProviderModelClient { List<String> fetchModels(InterfaceType type, String baseUrl, String apiKey); }
