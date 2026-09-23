package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CompileResultCollectorTest {
    @Test void acceptsStructuredResult() {
        var c = new CompileResultCollector(new ObjectMapper());
        c.begin();
        assertThat(c.accept(new CompileResultRequest("summary", List.of(), List.of())))
                .isEqualTo("compile_result accepted");
        assertThat(c.get().summary()).isEqualTo("summary");
    }

    @Test void rejectsMissingSummary() {
        var c = new CompileResultCollector(new ObjectMapper());
        c.begin();
        assertThat(c.accept(new CompileResultRequest("", List.of(), List.of()))).contains("summary");
    }

    @Test void rejectsMalformedKnowledgeItem() {
        var c = new CompileResultCollector(new ObjectMapper());
        c.begin();
        var item = new CompileResultRequest.KnowledgeResultItem("", "decision", "Title", "Summary", "create", "candidate", 1D, "wiki", "slug", "hash");
        assertThat(c.accept(new CompileResultRequest("summary", List.of(item), List.of())))
                .contains("itemKey");
        assertThat(c.get()).isNull();
    }

    @Test void rejectsConfidenceOutsideRange() {
        var c = new CompileResultCollector(new ObjectMapper());
        c.begin();
        var item = new CompileResultRequest.KnowledgeResultItem("key", "decision", "Title", "Summary", "create", "candidate", 1.1D, "wiki", "slug", "hash");
        assertThat(c.accept(new CompileResultRequest("summary", List.of(item), List.of())))
                .contains("confidence");
    }
}
