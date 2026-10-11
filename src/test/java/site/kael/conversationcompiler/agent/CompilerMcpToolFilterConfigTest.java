package site.kael.conversationcompiler.agent;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.McpConnectionInfo;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CompilerMcpToolFilterConfigTest {
    @Test
    void allowsOnlyKnowledgeTools() {
        var filter = new CompilerMcpToolFilterConfig().compilerMcpToolFilterBean();
        var connection = mock(McpConnectionInfo.class);
        var schema = Map.<String, Object>of("type", "object");
        var search = McpSchema.Tool.builder("okf_search", schema).title("").description("").build();
        var delete = McpSchema.Tool.builder("okf_delete_page", schema).title("").description("").build();

        assertThat(filter.test(connection, search)).isTrue();
        assertThat(filter.test(connection, delete)).isFalse();
    }
}
