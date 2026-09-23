package site.kael.conversationcompiler.manager;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.agent.*;
import site.kael.conversationcompiler.domain.*;
import site.kael.conversationcompiler.domain.compiler.*;
import site.kael.conversationcompiler.domain.settings.*;
import site.kael.conversationcompiler.manager.compiler.CompileRunWorker;
import site.kael.conversationcompiler.repository.EventRepository;
import site.kael.conversationcompiler.repository.compiler.*;
import site.kael.conversationcompiler.repository.settings.KnowledgeSettingsRepository;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompileRunWorkerTest {
    @Test void completesRunAndAdvancesVersion() {
        var runs=mock(CompileRunRepository.class); var execution=mock(CompileRunExecutionRepository.class); var events=mock(EventRepository.class); var settings=mock(KnowledgeSettingsRepository.class); var agent=mock(CompilerAgentService.class);
        var run=new CompileRun(1,"s","title",1,2,0,0,2,CompileRunStatus.pending,"queued",0,CompileTriggerType.manual,null,0,null,null,null,0,null,"mvp","","");
        when(runs.findById(1)).thenReturn(Optional.of(run)); when(settings.find()).thenReturn(Optional.of(new KnowledgeCompileSettings("p","P","m",1,"prompt",true,""))); when(events.findBySessionIdAndVersionRange(anyString(),anyLong(),anyLong())).thenReturn(List.of()); when(agent.compile(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyList(),any())).thenReturn(new CompileResultRequest("summary",List.of(),List.of()));
        when(execution.markRunning(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        new CompileRunWorker(runs,execution,events,settings,agent).execute(1);
        verify(events).findBySessionIdAndVersionRange("s",1,2);
        verify(execution).markCompleted(anyLong(),any()); verify(execution).advanceCompiledVersion("s",2);
    }
    @Test void warningsCompleteWithWarningsAndStillAdvanceVersion() {
        var runs=mock(CompileRunRepository.class); var execution=mock(CompileRunExecutionRepository.class); var events=mock(EventRepository.class); var settings=mock(KnowledgeSettingsRepository.class); var agent=mock(CompilerAgentService.class);
        var run=new CompileRun(1,"s","title",1,2,0,0,2,CompileRunStatus.pending,"queued",0,CompileTriggerType.manual,null,0,null,null,null,0,null,"mvp","","");
        when(runs.findById(1)).thenReturn(Optional.of(run));
        when(settings.find()).thenReturn(Optional.of(new KnowledgeCompileSettings("p","P","m",1,"prompt",true,"")));
        when(events.findBySessionIdAndVersionRange(anyString(),anyLong(),anyLong())).thenReturn(List.of());
        when(agent.compile(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyList(),any()))
                .thenReturn(new CompileResultRequest("summary",List.of(),List.of("one item was skipped")));
        when(execution.markRunning(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        new CompileRunWorker(runs,execution,events,settings,agent).execute(1);
        verify(execution).markCompleted(anyLong(), argThat(result -> result.warnings().contains("one item was skipped")));
        verify(execution).advanceCompiledVersion("s",2);
    }

    @Test void failureDoesNotAdvanceVersion() {
        var runs=mock(CompileRunRepository.class); var execution=mock(CompileRunExecutionRepository.class); var events=mock(EventRepository.class); var settings=mock(KnowledgeSettingsRepository.class); var agent=mock(CompilerAgentService.class);
        var run=new CompileRun(1,"s","title",1,2,0,0,2,CompileRunStatus.pending,"queued",0,CompileTriggerType.manual,null,0,null,null,null,0,null,"mvp","","");
        when(runs.findById(1)).thenReturn(Optional.of(run)); when(settings.find()).thenReturn(Optional.of(new KnowledgeCompileSettings("p","P","m",1,"prompt",true,""))); when(events.findBySessionIdAndVersionRange(anyString(),anyLong(),anyLong())).thenReturn(List.of()); when(agent.compile(anyLong(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong(),anyList(),any())).thenThrow(new RuntimeException("429"));
        when(execution.markRunning(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        new CompileRunWorker(runs,execution,events,settings,agent).execute(1);
        verify(execution).markFailed(anyLong(),contains("429")); verify(execution,never()).advanceCompiledVersion(anyString(),anyLong());
    }
}
