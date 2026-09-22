package site.kael.conversationcompiler.domain.settings;
public record KnowledgeSettingsRequest(String providerId, String modelName, Integer intervalMinutes, String prompt, Boolean enabled) {}
