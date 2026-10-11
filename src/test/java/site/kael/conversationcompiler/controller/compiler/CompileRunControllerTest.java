package site.kael.conversationcompiler.controller.compiler;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.kael.conversationcompiler.controller.ApiExceptionHandler;
import site.kael.conversationcompiler.domain.compiler.CompileRunAttempt;
import site.kael.conversationcompiler.domain.compiler.CompileRun;
import site.kael.conversationcompiler.domain.compiler.CompileRunStatus;
import site.kael.conversationcompiler.domain.compiler.CompileTriggerType;
import site.kael.conversationcompiler.domain.compiler.CompileRunTraceEntry;
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
    void returnsLiveTraceForCompileRun() throws Exception {
        when(service.trace(42)).thenReturn(List.of(new CompileRunTraceEntry(1, 42, 9L, "model_call", "assistant",
                "模型请求", "正在等待模型响应…", "running", "created", "updated")));
        mvc.perform(get("/api/v1/compile-runs/42/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("model_call"))
                .andExpect(jsonPath("$[0].status").value("running"));
        verify(service).trace(42);
    }

    @Test
    void exposesWarningDetailsWithoutExposingTheFullStoredResult() throws Exception {
        var run = run(CompileRunStatus.completed_with_warnings, List.of("未返回内容哈希", "只整理已有证据"));
        when(service.get(42)).thenReturn(run);
        when(service.list(null, null, 50, 0)).thenReturn(List.of(run));
        mvc.perform(get("/api/v1/compile-runs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed_with_warnings"))
                .andExpect(jsonPath("$.warnings[0]").value("未返回内容哈希"))
                .andExpect(jsonPath("$.warnings[1]").value("只整理已有证据"))
                .andExpect(jsonPath("$.resultJson").doesNotExist())
                .andExpect(jsonPath("$.result_json").doesNotExist());
        mvc.perform(get("/api/v1/compile-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].warnings[0]").value("未返回内容哈希"));
    }

    @Test
    void exposesAnEmptyArrayForRunsWithoutWarningDetails() throws Exception {
        when(service.get(42)).thenReturn(run(CompileRunStatus.completed, null));
        mvc.perform(get("/api/v1/compile-runs/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.warnings").isArray())
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    private CompileRun run(CompileRunStatus status, List<String> warnings) {
        return new CompileRun(42, "session", "title", 1, 3, 0, 0, 3, status, "completed", 100,
                CompileTriggerType.manual, "done", 1, null, null, null, 0, null, "mvp", "", "", warnings);
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
