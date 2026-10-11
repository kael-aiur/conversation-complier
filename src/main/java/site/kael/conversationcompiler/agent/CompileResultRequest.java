package site.kael.conversationcompiler.agent;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record CompileResultRequest(
        @JsonPropertyDescription(CompileResultSemantics.SUMMARY_DESCRIPTION) String summary,
        List<KnowledgeResultItem> items,
        @JsonPropertyDescription(CompileResultSemantics.WARNINGS_DESCRIPTION) List<String> warnings) {
    public record KnowledgeResultItem(String itemKey, String itemType, String title, String summary,
                                      String action, String status, Double confidence, String wiki,
                                      String slug,
                                      @JsonPropertyDescription(CompileResultSemantics.CONTENT_HASH_DESCRIPTION) String contentHash) {}
}
