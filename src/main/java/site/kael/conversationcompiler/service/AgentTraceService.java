package site.kael.conversationcompiler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import site.kael.conversationcompiler.common.BadRequestException;
import site.kael.conversationcompiler.domain.IngestResult;
import site.kael.conversationcompiler.domain.SessionMetadata;
import site.kael.conversationcompiler.manager.EventIngestionManager;
import site.kael.conversationcompiler.manager.SessionManager;

@Service
public class AgentTraceService {
    private final ObjectMapper mapper;
    private final EventIngestionManager eventManager;
    private final SessionManager sessionManager;
    public AgentTraceService(ObjectMapper mapper, EventIngestionManager eventManager, SessionManager sessionManager) { this.mapper = mapper; this.eventManager = eventManager; this.sessionManager = sessionManager; }
    public IngestResult ingestEvents(String ndjson) { return eventManager.ingest(ndjson); }
    public void ingestSession(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            String id = text(n, "session_id");
            sessionManager.register(new SessionMetadata(id, number(n,"started_at"), number(n,"ended_at"), textOrEmpty(n,"agent_name"), textOrEmpty(n,"command"), textOrEmpty(n,"workspace_id"), textOrEmpty(n,"tenant_id")));
        } catch (Exception e) { throw new BadRequestException(e.getMessage() == null ? "invalid session metadata" : e.getMessage()); }
    }
    private String text(JsonNode n, String field) { String value = textOrEmpty(n, field); if (value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value; }
    private String textOrEmpty(JsonNode n, String field) { JsonNode v=n.get(field); return v != null && v.isTextual() ? v.asText() : ""; }
    private Double number(JsonNode n, String field) { JsonNode v=n.get(field); return v != null && v.isNumber() ? v.asDouble() : null; }
}
