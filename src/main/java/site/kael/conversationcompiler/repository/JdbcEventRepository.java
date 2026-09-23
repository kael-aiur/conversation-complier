package site.kael.conversationcompiler.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.domain.ConversationEvent;

import java.util.List;

@Repository
public class JdbcEventRepository implements EventRepository {
    private final JdbcTemplate jdbc;
    public JdbcEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean insert(String sessionId, String eventId, String eventType, double timestamp, String receivedAt, String payloadJson) {
        return jdbc.update("INSERT OR IGNORE INTO conversation_events(session_id,event_id,event_type,event_timestamp,received_at,payload_json) VALUES (?,?,?,?,?,?)", sessionId, eventId, eventType, timestamp, receivedAt, payloadJson) == 1;
    }

    @Override
    public List<ConversationEvent> findBySessionIdAndVersionRange(String sessionId, long fromVersion, long toVersion) {
        if (toVersion < fromVersion) return List.of();
        long count = toVersion - fromVersion + 1;
        if (count > Integer.MAX_VALUE || fromVersion < 1 || fromVersion > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("event version range is outside supported bounds");
        }
        return findBySessionId(sessionId, (int) count, (int) (fromVersion - 1));
    }

    @Override
    public List<ConversationEvent> findBySessionId(String sessionId, int limit, int offset) {
        return jdbc.query("SELECT id,session_id,event_id,event_type,event_timestamp,received_at,payload_json FROM conversation_events WHERE session_id=? ORDER BY id ASC LIMIT ? OFFSET ?", (rs, rowNum) -> new ConversationEvent(rs.getLong("id"), rs.getString("session_id"), rs.getString("event_id"), rs.getString("event_type"), rs.getDouble("event_timestamp"), rs.getString("received_at"), rs.getString("payload_json")), sessionId, limit, offset);
    }
}
