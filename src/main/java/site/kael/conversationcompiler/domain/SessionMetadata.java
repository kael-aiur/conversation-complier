package site.kael.conversationcompiler.domain;

public record SessionMetadata(
        String sessionId,
        Double startedAt,
        Double endedAt,
        String agentName,
        String command,
        String workspaceId,
        String tenantId
) {}
