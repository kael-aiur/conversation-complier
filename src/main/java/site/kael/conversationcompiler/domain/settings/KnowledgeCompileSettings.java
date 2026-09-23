package site.kael.conversationcompiler.domain.settings;

import java.util.List;

public record KnowledgeCompileSettings(String providerId, String providerName, String modelName,
                                       List<KnowledgeModelSelection> models,
                                       int intervalMinutes, String prompt, boolean enabled, String updatedAt) {
    public KnowledgeCompileSettings {
        models = models == null ? List.of() : List.copyOf(models);
    }

    /** Backward-compatible constructor for the previous single-model representation. */
    public KnowledgeCompileSettings(String providerId, String providerName, String modelName,
                                    int intervalMinutes, String prompt, boolean enabled, String updatedAt) {
        this(providerId, providerName, modelName,
                providerId == null || modelName == null ? List.of() : List.of(new KnowledgeModelSelection(providerId, providerName, modelName)),
                intervalMinutes, prompt, enabled, updatedAt);
    }
}
