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
        try {
            return accept(mapper.readValue(json, CompileResultRequest.class));
        } catch (Exception e) {
            return "compile_result rejected: invalid JSON";
        }
    }

    public String accept(CompileResultRequest result) {
        String validationError = validate(result);
        if (validationError != null) return "compile_result rejected: " + validationError;
        if (current.get() == null) begin();
        current.get().set(result);
        return "compile_result accepted";
    }

    public CompileResultRequest get() { return current.get() == null ? null : current.get().get(); }
    public void clear() { current.remove(); }

    private String validate(CompileResultRequest result) {
        if (result == null) return "result is required";
        if (result.summary() == null || result.summary().isBlank()) return "summary is required";
        if (result.items() == null) return "items is required";
        for (int i = 0; i < result.items().size(); i++) {
            var item = result.items().get(i);
            if (item == null) return "items[" + i + "] is required";
            if (blank(item.itemKey())) return "items[" + i + "].itemKey is required";
            if (blank(item.itemType())) return "items[" + i + "].itemType is required";
            if (blank(item.title())) return "items[" + i + "].title is required";
            if (blank(item.summary())) return "items[" + i + "].summary is required";
            if (blank(item.action())) return "items[" + i + "].action is required";
            if (blank(item.status())) return "items[" + i + "].status is required";
            if (item.confidence() != null && (item.confidence().isNaN() || item.confidence().isInfinite()
                    || item.confidence() < 0 || item.confidence() > 1)) {
                return "items[" + i + "].confidence must be between 0 and 1";
            }
        }
        if (result.warnings() != null && result.warnings().stream().anyMatch(value -> value == null)) {
            return "warnings cannot contain null";
        }
        return null;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
