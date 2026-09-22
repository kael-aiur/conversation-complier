package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class CompileResultCollector {
    private final ObjectMapper mapper;
    private final ThreadLocal<AtomicReference<CompileResultRequest>> current = new ThreadLocal<>();
    public CompileResultCollector(ObjectMapper mapper) { this.mapper = mapper; }
    public void begin() { current.set(new AtomicReference<>()); }
    public String accept(String json) {
        try { return accept(mapper.readValue(json, CompileResultRequest.class)); }
        catch (Exception e) { return "compile_result rejected: invalid JSON"; }
    }
    public String accept(CompileResultRequest result) {
        if (result == null || result.summary() == null || result.summary().isBlank()) return "compile_result rejected: summary is required";
        if (result.items() == null) return "compile_result rejected: items is required";
        if (current.get() == null) begin();
        current.get().set(result);
        return "compile_result accepted";
    }
    public CompileResultRequest get() { return current.get() == null ? null : current.get().get(); }
    public void clear() { current.remove(); }
}
