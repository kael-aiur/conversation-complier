package site.kael.conversationcompiler.service.compiler;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.common.ConflictException;
import site.kael.conversationcompiler.domain.compiler.*;
import site.kael.conversationcompiler.manager.ConversationQueryManager;
import site.kael.conversationcompiler.repository.compiler.CompileRunRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CompileRunServiceTest {
    private final CompileRunRepository runs = mock(CompileRunRepository.class);
    private final ConversationQueryManager conversations = mock(ConversationQueryManager.class);
    private final CompileRunService service = new CompileRunService(runs, conversations);

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
