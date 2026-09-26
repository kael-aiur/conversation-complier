package site.kael.conversationcompiler.controller.compiler;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import site.kael.conversationcompiler.domain.compiler.*;
import site.kael.conversationcompiler.service.compiler.CompileRunService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/compile-runs")
public class CompileRunController {
    private final CompileRunService service;
    public CompileRunController(CompileRunService service) { this.service = service; }
    @GetMapping public List<CompileRun> list(@RequestParam(required = false) String sessionId, @RequestParam(required = false) String status, @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) { return service.list(sessionId, status, limit, offset); }
    @GetMapping("/{id}") public CompileRun get(@PathVariable long id) { return service.get(id); }
    @GetMapping("/{id}/knowledge-items") public List<CompileRunKnowledgeItem> knowledge(@PathVariable long id) { return service.knowledge(id); }
    @GetMapping("/{id}/attempts") public List<CompileRunAttempt> attempts(@PathVariable long id) { return service.attempts(id); }
    @PostMapping("/{id}/retry") public ResponseEntity<Map<String,Object>> retry(@PathVariable long id) { long newId = service.retry(id); return ResponseEntity.accepted().body(Map.of("id", newId, "status", "pending")); }

}
