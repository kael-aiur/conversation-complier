package site.kael.conversationcompiler.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import site.kael.conversationcompiler.domain.IngestResult;
import site.kael.conversationcompiler.service.AgentTraceService;

import java.util.Map;

@RestController
public class AgentTraceCompatibilityController {
    private final AgentTraceService service;
    public AgentTraceCompatibilityController(AgentTraceService service) { this.service = service; }

    @PostMapping(value = "/events", consumes = "application/x-ndjson", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestResult> events(@RequestBody String body) { return ResponseEntity.accepted().body(service.ingestEvents(body)); }

    @PostMapping(value = "/sessions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> sessions(@RequestBody String body) { service.ingestSession(body); return ResponseEntity.ok(Map.of("status", "ok")); }
}
