package site.kael.conversationcompiler.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import site.kael.conversationcompiler.domain.ConversationEvent;
import site.kael.conversationcompiler.repository.settings.ModelProviderRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class CompilerAgentServiceTest {
    @Test
    void sendsRunIdentityAndRequirementsToAgentAndExposesMcpAndCompileResultTools() throws Exception {
        var factory = mock(CompilerChatClientFactory.class);
        var client = mock(ChatClient.class);
        var request = mock(ChatClient.ChatClientRequestSpec.class);
        var response = mock(ChatClient.CallResponseSpec.class);
        var providerRepository = mock(ModelProviderRepository.class);
        var mcpProvider = mock(ToolCallbackProvider.class);
        var mcpTool = mock(ToolCallback.class);
        var mcpDefinition = DefaultToolDefinition.builder().name("okf_search").description("search").inputSchema("{}").build();
        when(mcpTool.getToolDefinition()).thenReturn(mcpDefinition);
        when(mcpProvider.getToolCallbacks()).thenReturn(new ToolCallback[]{mcpTool});
        when(providerRepository.findAll()).thenReturn(List.of());
        when(factory.create("provider", "model")).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.system(anyString())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.toolCallbacks(anyList())).thenReturn(request);
        when(response.content()).thenReturn("");

        var capturedTools = new AtomicReference<List<ToolCallback>>();
        when(request.toolCallbacks(anyList())).thenAnswer(invocation -> {
            capturedTools.set(invocation.getArgument(0));
            return request;
        });
        var mapper = new ObjectMapper();
        when(request.call()).thenAnswer(invocation -> {
            var compileResult = capturedTools.get().stream()
                    .filter(tool -> tool.getToolDefinition().name().equals("compile_result"))
                    .findFirst().orElseThrow();
            compileResult.call(mapper.writeValueAsString(new CompileResultRequest("done", List.of(), List.of())));
            return response;
        });

        var collector = new CompileResultCollector(mapper);
        var service = new CompilerAgentService(factory, mapper, collector, mockProvider(mcpProvider), providerRepository);
        var result = service.compile(42L, "整理稳定事实", "provider", "model", "session",
                3, 4, List.of(new ConversationEvent(1, "session", "event", "user_prompt", 1D, "now", "payload")),
                new ModelFailoverRunner.AttemptObserver() {
                    public void started(String candidate, int attempt) { }
                    public void finished(String candidate, int attempt, String status, Throwable error) { }
                });

        assertThat(result.summary()).isEqualTo("done");
        assertThat(capturedTools.get()).extracting(tool -> tool.getToolDefinition().name())
                .contains("okf_search", "compile_result");
        var systemCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var userCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(request).system(systemCaptor.capture());
        verify(request).user(userCaptor.capture());
        assertThat(systemCaptor.getValue()).contains("整理稳定事实", "不可信输入", "compile_result");
        assertThat(userCaptor.getValue()).contains("\"compileRunId\":42", "\"fromVersion\":3", "\"toVersion\":4");
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ToolCallbackProvider> mockProvider(ToolCallbackProvider provider) {
        var result = mock(ObjectProvider.class);
        when(result.orderedStream()).thenReturn(Stream.of(provider));
        return result;
    }
}
