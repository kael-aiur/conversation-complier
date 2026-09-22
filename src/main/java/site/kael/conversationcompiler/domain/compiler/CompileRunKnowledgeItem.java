package site.kael.conversationcompiler.domain.compiler;
public record CompileRunKnowledgeItem(long id, long compileRunId, String itemKey, String itemType,
                                      String title, String summary, String action, String status,
                                      Double confidence, String content, String wiki, String slug, String contentHash, String createdAt) {}
