package site.kael.conversationcompiler.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import site.kael.conversationcompiler.domain.SessionMetadata;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:file:repository-test?mode=memory&cache=shared",
        "spring.sql.init.mode=always"
})
class RepositoryIntegrationTest {
    @Autowired ConversationRepository conversations;
    @Autowired EventRepository events;

    @Test
    void persistsConversationAndEventsForQueries() {
        conversations.createOrUpdate(new SessionMetadata("repo-session", 1d, null, "Codex", "cmd", "workspace", ""));
        assertThat(events.insert("repo-session", "evt-1", "user_prompt", 2d, "now", "{\"event_type\":\"user_prompt\"}")).isTrue();
        conversations.updateAfterEvent("repo-session", 2d, "A title");
        assertThat(conversations.findById("repo-session")).isPresent();
        assertThat(events.findBySessionId("repo-session", 10, 0)).hasSize(1);
        assertThat(events.findBySessionIdAndVersionRange("repo-session", 1, 1)).hasSize(1);
        assertThat(events.findBySessionIdAndVersionRange("repo-session", 2, 2)).isEmpty();
        assertThat(events.insert("repo-session", "evt-1", "user_prompt", 2d, "now", "{}")).isFalse();
    }
}
