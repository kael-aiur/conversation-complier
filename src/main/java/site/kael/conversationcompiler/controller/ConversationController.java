package site.kael.conversationcompiler.controller;

import org.springframework.web.bind.annotation.*;
import site.kael.conversationcompiler.domain.ConversationEvent;
import site.kael.conversationcompiler.domain.ConversationSummary;
import site.kael.conversationcompiler.service.ConversationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService service;
    public ConversationController(ConversationService service) { this.service = service; }
    @GetMapping
    public List<ConversationSummary> list(@RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return service.list(limit, offset); }
    @GetMapping("/{sessionId}")
    public ConversationSummary get(@PathVariable String sessionId) { return service.get(sessionId); }
    @GetMapping("/{sessionId}/events")
    public List<ConversationEvent> events(@PathVariable String sessionId, @RequestParam(defaultValue = "500") int limit, @RequestParam(defaultValue = "0") int offset) { return service.events(sessionId, limit, offset); }
}
