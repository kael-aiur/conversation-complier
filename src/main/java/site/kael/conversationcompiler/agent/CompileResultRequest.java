package site.kael.conversationcompiler.agent;

import java.util.List;

public record CompileResultRequest(String summary, List<KnowledgeResultItem> items, List<String> warnings) {
    public record KnowledgeResultItem(String itemKey, String itemType, String title, String summary,
                                      String action, String status, Double confidence, String wiki,
                                      String slug, String contentHash) {}
}
