package site.kael.conversationcompiler.manager;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.common.NotFoundException;
import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.repository.ConversationRepository;
import site.kael.conversationcompiler.repository.EventRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConversationQueryManagerTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final ConversationQueryManager manager = new ConversationQueryManager(conversations, events);

    @Test
    void clampsPaginationAndListsConversations() {
        when(conversations.findAll(500, 0)).thenReturn(List.of());
        assertThat(manager.list(900, -5)).isEmpty();
        verify(conversations).findAll(500, 0);
    }

    @Test
    void requiresConversationBeforeListingEvents() {
        when(conversations.findById("missing")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> manager.events("missing", 10, 0)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void calculatesProgressFromVersions() {
        ConversationSummary summary = new ConversationSummary("s", "agent-trace", "title", "Codex", "", "", 1d, 2d, 4, 10, 7, "active", null, "", "");
        assertThat(summary.progress()).isEqualTo(70);
    }
}
