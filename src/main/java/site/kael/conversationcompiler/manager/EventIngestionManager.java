package site.kael.conversationcompiler.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.kael.conversationcompiler.common.BadRequestException;
import site.kael.conversationcompiler.domain.IngestResult;
import site.kael.conversationcompiler.repository.ConversationRepository;
import site.kael.conversationcompiler.repository.EventRepository;

import java.time.Instant;

@Component
public class EventIngestionManager {
    private final ObjectMapper mapper;
    private final ConversationRepository conversations;
    private final EventRepository events;

    public EventIngestionManager(ObjectMapper mapper, ConversationRepository conversations, EventRepository events) {
        this.mapper = mapper; this.conversations = conversations; this.events = events;
    }

    @Transactional
    public IngestResult ingest(String ndjson) {
        if (ndjson == null || ndjson.isBlank()) throw new BadRequestException("NDJSON body must not be empty");
        int accepted = 0, duplicates = 0, errors = 0;
        for (String line : ndjson.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                JsonNode node = mapper.readTree(line);
                String sessionId = requiredText(node, "session_id");
                String eventId = requiredText(node, "event_id");
                String eventType = requiredText(node, "event_type");
                if (sessionId.length() > 128 || eventId.length() > 256) throw new IllegalArgumentException("identifier too long");
                JsonNode timestampNode = node.get("timestamp");
                if (timestampNode == null || !timestampNode.isNumber()) throw new IllegalArgumentException("timestamp must be a number");
                double timestamp = timestampNode.asDouble();
                conversations.ensureExists(sessionId, timestamp);
                if (events.insert(sessionId, eventId, eventType, timestamp, Instant.now().toString(), line)) {
                    accepted++;
                    conversations.updateAfterEvent(sessionId, timestamp, extractTitle(node));
                } else duplicates++;
            } catch (Exception ignored) { errors++; }
        }
        return new IngestResult(accepted, duplicates, errors);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.asText();
    }

    private String extractTitle(JsonNode node) {
        JsonNode data = node.get("data");
        if (data != null && data.isObject()) {
            for (String field : new String[]{"title", "prompt", "content", "message"}) {
                JsonNode value = data.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText().substring(0, Math.min(120, value.asText().length()));
            }
        }
        return null;
    }
}
