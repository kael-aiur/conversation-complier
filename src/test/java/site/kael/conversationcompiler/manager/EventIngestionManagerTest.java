package site.kael.conversationcompiler.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.domain.IngestResult;
import site.kael.conversationcompiler.repository.ConversationRepository;
import site.kael.conversationcompiler.repository.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventIngestionManagerTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final EventIngestionManager manager = new EventIngestionManager(new ObjectMapper(), conversations, events);

    @Test
    void acceptsValidEventsAndUpdatesConversation() {
        when(events.insert(anyString(), anyString(), anyString(), anyDouble(), anyString(), anyString())).thenReturn(true);
        IngestResult result = manager.ingest("{\"event_type\":\"user_prompt\",\"event_id\":\"evt-1\",\"session_id\":\"sess-1\",\"timestamp\":1720000000,\"data\":{\"prompt\":\"设计后端架构\"}}\n");
        assertThat(result).isEqualTo(new IngestResult(1, 0, 0));
        verify(conversations).ensureExists("sess-1", 1720000000d);
        verify(conversations).updateAfterEvent(eq("sess-1"), eq(1720000000d), eq("设计后端架构"));
    }

    @Test
    void countsDuplicateEventsWithoutUpdatingConversation() {
        when(events.insert(anyString(), anyString(), anyString(), anyDouble(), anyString(), anyString())).thenReturn(false);
        IngestResult result = manager.ingest("{\"event_type\":\"tool_result\",\"event_id\":\"evt-1\",\"session_id\":\"sess-1\",\"timestamp\":1}");
        assertThat(result.duplicates()).isEqualTo(1);
        verify(conversations, never()).updateAfterEvent(anyString(), anyDouble(), any());
    }

    @Test
    void countsMalformedLinesAsErrorsAndRejectsEmptyBody() {
        IngestResult result = manager.ingest("not-json\n{\"event_id\":\"missing-fields\"}");
        assertThat(result).isEqualTo(new IngestResult(0, 0, 2));
        verifyNoInteractions(events);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.ingest(" "))
                .isInstanceOf(site.kael.conversationcompiler.common.BadRequestException.class);
    }
}
