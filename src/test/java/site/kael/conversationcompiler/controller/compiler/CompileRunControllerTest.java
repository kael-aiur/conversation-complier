package site.kael.conversationcompiler.controller.compiler;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.kael.conversationcompiler.controller.ApiExceptionHandler;
import site.kael.conversationcompiler.domain.compiler.CompileRunAttempt;
import site.kael.conversationcompiler.service.compiler.CompileRunService;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompileRunControllerTest {
    private final CompileRunService service = mock(CompileRunService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompileRunController(service))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void returnsOrderedAttemptsAndSanitizedFailureMessages() throws Exception {
        when(service.attempts(42)).thenReturn(List.of(
                new CompileRunAttempt(1, 42, 1, "provider", "Provider", "gemini-bad", "failed",
                        "IllegalStateException", "HTTP 400: invalid request", 0, 0, "start", "finish"),
                new CompileRunAttempt(2, 42, 2, "provider", "Provider", "fallback-ok", "completed",
                        null, null, 0, 0, "start2", "finish2")));

        mvc.perform(get("/api/v1/compile-runs/42/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attemptNumber").value(1))
                .andExpect(jsonPath("$[0].modelName").value("gemini-bad"))
                .andExpect(jsonPath("$[0].errorMessage").value("HTTP 400: invalid request"))
                .andExpect(jsonPath("$[1].status").value("completed"));
        verify(service).attempts(42);
    }

    @Test
    void retryReturnsTheSameCompileRunId() throws Exception {
        when(service.retry(42)).thenReturn(42L);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/compile-runs/42/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.reusedRecord").value(true));
        verify(service).retry(42);
    }
}
