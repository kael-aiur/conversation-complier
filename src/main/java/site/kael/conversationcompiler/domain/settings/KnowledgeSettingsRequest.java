package site.kael.conversationcompiler.domain.settings;

import java.util.List;

public record KnowledgeSettingsRequest(String providerId, String modelName,
                                       List<KnowledgeModelSelectionRequest> models,
                                       Integer intervalMinutes, String prompt, Boolean enabled) {
    public KnowledgeSettingsRequest(String providerId, String modelName, Integer intervalMinutes, String prompt, Boolean enabled) {
        this(providerId, modelName, null, intervalMinutes, prompt, enabled);
    }
}
