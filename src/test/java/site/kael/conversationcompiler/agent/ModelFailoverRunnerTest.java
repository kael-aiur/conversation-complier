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

    @Test void doesNotFailOverOnBadRequest() {
        assertThatThrownBy(() -> runner().run(List.of("a", "b"), m -> { throw new RuntimeException("HTTP 400"); }))
                .hasMessageContaining("HTTP 400");
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
