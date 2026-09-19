package site.kael.conversationcompiler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.common.BadRequestException;
import site.kael.conversationcompiler.domain.IngestResult;
import site.kael.conversationcompiler.manager.EventIngestionManager;
import site.kael.conversationcompiler.manager.SessionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentTraceServiceTest {
    private final EventIngestionManager events = mock(EventIngestionManager.class);
    private final SessionManager sessions = mock(SessionManager.class);
    private final AgentTraceService service = new AgentTraceService(new ObjectMapper(), events, sessions);

    @Test
    void delegatesEventIngestion() {
        when(events.ingest("payload")).thenReturn(new IngestResult(1, 0, 0));
        assertThat(service.ingestEvents("payload").accepted()).isEqualTo(1);
    }

    @Test
    void parsesAndDelegatesSessionMetadata() {
        service.ingestSession("{\"session_id\":\"sess-1\",\"started_at\":1,\"agent_name\":\"Codex\"}");
        verify(sessions).register(any());
    }

    @Test
    void rejectsSessionWithoutId() {
        assertThatThrownBy(() -> service.ingestSession("{\"agent_name\":\"Codex\"}"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(sessions);
    }
}
