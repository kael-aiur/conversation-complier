package site.kael.conversationcompiler.service.compiler;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.common.ConflictException;
import site.kael.conversationcompiler.domain.compiler.*;
import site.kael.conversationcompiler.manager.ConversationQueryManager;
import site.kael.conversationcompiler.repository.compiler.CompileRunRepository;
import site.kael.conversationcompiler.repository.compiler.CompileRunTraceRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CompileRunServiceTest {
    private final CompileRunRepository runs = mock(CompileRunRepository.class);
    private final ConversationQueryManager conversations = mock(ConversationQueryManager.class);
    private final CompileRunService service = new CompileRunService(runs, conversations);
    private final CompileRunTraceRepository traces = mock(CompileRunTraceRepository.class);

    @Test
    void traceRetrievalRequiresExistingRunAndReturnsPersistedMessages() {
        when(runs.findById(17)).thenReturn(Optional.of(run(17, CompileRunStatus.running)));
        var entry = new CompileRunTraceEntry(1, 17, 4L, "model_call", "assistant", "模型请求", "处理中", "running", "created", "updated");
        when(traces.findByRunId(17)).thenReturn(java.util.List.of(entry));
        var traceService = new CompileRunService(runs, conversations, traces);
        assertThat(traceService.trace(17)).containsExactly(entry);
        verify(traces).findByRunId(17);
    }

    @Test
    void retryRequeuesSameFailedRecordInsteadOfCreatingAnother() {
        var failed = run(17, CompileRunStatus.failed);
        when(runs.findById(17)).thenReturn(Optional.of(failed));
        when(runs.requeueFailed(17)).thenReturn(true);

        assertThat(service.retry(17)).isEqualTo(17);
        verify(runs).requeueFailed(17);
        verify(runs, never()).createPending(anyString(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    void retryRejectsWhenRecordIsNoLongerFailed() {
        when(runs.findById(17)).thenReturn(Optional.of(run(17, CompileRunStatus.running)));
        assertThatThrownBy(() -> service.retry(17))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only failed");
        verify(runs, never()).requeueFailed(anyLong());
    }

    @Test
    void retryRejectsWhenAnotherActiveRunPreventsRequeue() {
        when(runs.findById(17)).thenReturn(Optional.of(run(17, CompileRunStatus.failed)));
        when(runs.requeueFailed(17)).thenReturn(false);
        assertThatThrownBy(() -> service.retry(17))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active compile run");
    }

    private CompileRun run(long id, CompileRunStatus status) {
        return new CompileRun(id, "session", "title", 1, 3, 0, 0, 3, status, "failed", 0,
                CompileTriggerType.manual, null, 0, null, null, null, 0, "previous error", "mvp", "", "");
    }
}
