package site.kael.conversationcompiler.domain.settings;

public record KnowledgeCompileSettings(String providerId, String providerName, String modelName,
                                       int intervalMinutes, String prompt, boolean enabled, String updatedAt) {}
