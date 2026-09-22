package site.kael.conversationcompiler.domain.settings;
import java.util.List;
public record ModelProviderRequest(String name, String interfaceType, String baseUrl, String apiKey, List<String> models, String modelsFetchedAt, Boolean enabled) {}
