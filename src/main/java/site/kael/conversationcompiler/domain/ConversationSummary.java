package site.kael.conversationcompiler.domain;

public record ConversationSummary(
        String sessionId,
        String source,
        String title,
        String agentName,
        String command,
        String workspaceId,
        Double firstEventAt,
        Double lastEventAt,
        long eventCount,
        long version,
        long compiledVersion,
        String status,
        String lastCompiledAt,
        String createdAt,
        String updatedAt
) {
    public int progress() {
        if (version <= 0) return 0;
        return (int) Math.min(100, Math.round(compiledVersion * 100.0 / version));
    }
}
