package site.kael.conversationcompiler.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.kael.conversationcompiler.domain.IngestResult;
import site.kael.conversationcompiler.service.AgentTraceService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AgentTraceCompatibilityControllerTest {
    private final AgentTraceService service = mock(AgentTraceService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AgentTraceCompatibilityController(service)).build();

    @Test
    void acceptsAgentTraceNdjsonEvents() throws Exception {
        when(service.ingestEvents(anyString())).thenReturn(new IngestResult(2, 1, 0));
        mvc.perform(post("/events").contentType("application/x-ndjson").content("event-1\nevent-2"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.duplicates").value(1));
    }

    @Test
    void acceptsAgentTraceSessionMetadata() throws Exception {
        mvc.perform(post("/sessions").contentType(MediaType.APPLICATION_JSON).content("{\"session_id\":\"sess-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        verify(service).ingestSession(anyString());
    }
}
