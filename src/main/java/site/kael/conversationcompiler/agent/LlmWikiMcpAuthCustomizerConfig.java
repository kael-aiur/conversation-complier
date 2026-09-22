package site.kael.conversationcompiler.agent;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmWikiMcpAuthCustomizerConfig {
    @Bean
    public McpSyncHttpClientRequestCustomizer llmWikiMcpAuthHeaderCustomizer(
            @Value("${LLMWIKING_MCP_KEY:}") String mcpKey,
            @Value("${LLMWIKING_MCP_API_KEY:}") String apiKey,
            @Value("${LLMWIKING_MCP_ACCESS_CLIENT_ID:}") String accessId,
            @Value("${LLMWIKING_MCP_ACCESS_CLIENT_SECRET:}") String accessSecret) {
        return (builder, method, endpoint, body, context) -> {
            if (mcpKey != null && !mcpKey.isBlank()) builder.header("X-MCP-Key", mcpKey);
            if (apiKey != null && !apiKey.isBlank()) builder.header("X-API-Key", apiKey);
            if (accessId != null && !accessId.isBlank()) builder.header("CF-Access-Client-Id", accessId);
            if (accessSecret != null && !accessSecret.isBlank()) builder.header("CF-Access-Client-Secret", accessSecret);
        };
    }
}
