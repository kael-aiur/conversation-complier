package site.kael.conversationcompiler.domain.settings;

/** One ordered provider/model candidate used by the compiler failover chain. */
public record KnowledgeModelSelection(String providerId, String providerName, String modelName) {}
