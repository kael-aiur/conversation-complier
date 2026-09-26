package site.kael.conversationcompiler.agent;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ModelFailoverRunnerTest {
    @Test void retriesRateLimitOnSameModel() {
        var n = new AtomicInteger();
        String result = runner().run(List.of("a", "b"), m -> {
            if (n.getAndIncrement() == 0) throw new RuntimeException("HTTP 429");
            return m;
        });
        assertThat(result).isEqualTo("a");
    }

    @Test void honorsRetryAfterAndFailsOverAfterTwoRateLimitErrors() {
        var a = new AtomicInteger();
        var delays = new java.util.ArrayList<Long>();
        String result = new ModelFailoverRunner(delays::add).run(List.of("a", "b"), m -> {
            if (m.equals("a") && a.getAndIncrement() < 2) throw new RuntimeException("HTTP 429 Retry-After: 7");
            return m;
        });
        assertThat(result).isEqualTo("b");
        assertThat(delays).containsExactly(7_000L);
    }

    @Test void unauthorizedSkipsRetryOnCurrentModelAndFailsOver() {
        var calls = new AtomicInteger();
        String result = runner().run(List.of("bad", "good"), model -> {
            calls.incrementAndGet();
            if (model.equals("bad")) throw new RuntimeException("HTTP 401 unauthorized");
            return model;
        });
        assertThat(result).isEqualTo("good");
        assertThat(calls).hasValue(2);
    }

    @Test void skipsBadRequestingModelAndTriesNextConfiguredModel() {
        var calls = new java.util.ArrayList<String>();
        String result = runner().run(List.of("bad-request-model", "fallback-model"), model -> {
            calls.add(model);
            if (model.equals("bad-request-model")) throw new RuntimeException("400: * GenerateContentRequest.contents: contents is not specified");
            return model;
        });
        assertThat(result).isEqualTo("fallback-model");
        assertThat(calls).containsExactly("bad-request-model", "fallback-model");
    }

    @Test void badRequestsVisitEachConfiguredModelOnceBeforeFailing() {
        var calls = new java.util.ArrayList<String>();
        assertThatThrownBy(() -> runner().run(List.of("a", "b", "c"), model -> {
            calls.add(model);
            throw new RuntimeException("400: bad request");
        })).hasMessageContaining("all model candidates failed");
        assertThat(calls).containsExactly("a", "b", "c");
    }

    @Test void failsOverWhenModelDoesNotCallRequiredCompileResultTool() {
        var calls = new java.util.ArrayList<String>();
        String result = runner().run(List.of("no-tool-model", "tool-capable-fallback"), model -> {
            calls.add(model);
            if (model.equals("no-tool-model")) {
                throw new IllegalStateException("compiler agent failed: compiler agent did not call compile_result");
            }
            return model;
        });
        assertThat(result).isEqualTo("tool-capable-fallback");
        assertThat(calls).containsExactly("no-tool-model", "tool-capable-fallback");
    }

    @Test void retriesTimeoutAndServerErrors() {
        var calls = new AtomicInteger();
        String result = runner().run(List.of("a"), m -> {
            if (calls.getAndIncrement() == 0) throw new RuntimeException("HTTP 503");
            return m;
        });
        assertThat(result).isEqualTo("a");
    }

    private ModelFailoverRunner runner() { return new ModelFailoverRunner(ignored -> { }); }
}
