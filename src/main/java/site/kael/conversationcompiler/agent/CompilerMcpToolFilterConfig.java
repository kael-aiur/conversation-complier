package site.kael.conversationcompiler.agent;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class CompilerMcpToolFilterConfig {
    private static final Set<String> ALLOWED = Set.of(
            "okf_list_wikis", "okf_list_pages", "okf_read_concept", "okf_search",
            "okf_matrix_search", "okf_graph", "okf_write_concept");

    @Bean
    public McpToolFilter compilerMcpToolFilterBean() {
        return (McpConnectionInfo connection, McpSchema.Tool tool) -> ALLOWED.contains(tool.name());
    }
}
