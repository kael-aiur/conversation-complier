package site.kael.conversationcompiler.repository.settings;

import site.kael.conversationcompiler.domain.settings.InterfaceType;
import site.kael.conversationcompiler.domain.settings.ModelProvider;
import java.util.List;
import java.util.Optional;

public interface ModelProviderRepository {
    List<ModelProvider> findAll(); Optional<ModelProvider> findById(String id); boolean exists(String id);
    void save(String id, String name, InterfaceType type, String baseUrl, String encryptedKey, String fingerprint, boolean enabled, List<String> models, String fetchedAt);
    void delete(String id); List<String> findModels(String id); String findEncryptedApiKey(String id); String findApiKeyFingerprint(String id);
}
