package site.kael.conversationcompiler.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.kael.conversationcompiler.domain.ConversationEvent;
import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.service.ConversationService;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConversationControllerTest {
    private final ConversationService service = mock(ConversationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConversationController(service)).build();

    @Test
    void listsConversations() throws Exception {
        when(service.list(50, 0)).thenReturn(List.of(new ConversationSummary("sess-1", "agent-trace", "Title", "Codex", "", "", 1d, 2d, 3, 3, 1, "active", null, "", "")));
        mvc.perform(get("/api/v1/conversations")).andExpect(status().isOk()).andExpect(jsonPath("$[0].sessionId").value("sess-1"));
    }

    @Test
    void returnsConversationEventsForDrawer() throws Exception {
        when(service.events("sess-1", 500, 0)).thenReturn(List.of(new ConversationEvent(1, "sess-1", "evt-1", "user_prompt", 1, "now", "{}")));
        mvc.perform(get("/api/v1/conversations/sess-1/events")).andExpect(status().isOk()).andExpect(jsonPath("$[0].eventType").value("user_prompt"));
    }
}
