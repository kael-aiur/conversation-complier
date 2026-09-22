package site.kael.conversationcompiler.agent;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpRequest;
import static org.assertj.core.api.Assertions.assertThat;

class LlmWikiMcpAuthCustomizerTest {
 @Test void addsBearerHeader() {
  var customizer = new LlmWikiMcpAuthCustomizerConfig().llmWikiMcpAuthHeaderCustomizer("secret", "", "", "");
  var builder = HttpRequest.newBuilder(URI.create("https://example.test/mcp"));
  customizer.customize(builder, "POST", URI.create("https://example.test/mcp"), "{}", McpTransportContext.EMPTY);
  assertThat(builder.build().headers().firstValue("X-MCP-Key")).contains("secret");
 }
}
