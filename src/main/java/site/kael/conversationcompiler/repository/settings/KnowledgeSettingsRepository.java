package site.kael.conversationcompiler.repository.settings;

import site.kael.conversationcompiler.domain.settings.KnowledgeCompileSettings;
import site.kael.conversationcompiler.domain.settings.KnowledgeModelSelectionRequest;
import java.util.List;
import java.util.Optional;

public interface KnowledgeSettingsRepository {
    Optional<KnowledgeCompileSettings> find();
    void save(List<KnowledgeModelSelectionRequest> models, int interval, String prompt, boolean enabled);
    default void save(String providerId, String modelName, int interval, String prompt, boolean enabled) {
        save(List.of(new KnowledgeModelSelectionRequest(providerId, modelName)), interval, prompt, enabled);
    }
}
