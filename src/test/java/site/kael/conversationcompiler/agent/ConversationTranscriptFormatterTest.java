package site.kael.conversationcompiler.agent;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.domain.ConversationEvent;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ConversationTranscriptFormatterTest {
    private ConversationEvent e(String type,String json) {return new ConversationEvent(1,"s","e",type,1,"now",json);}

    @Test void rendersFixedLabelsWithActualMessagesAndToolArguments() {
        String text=ConversationTranscriptFormatter.format(List.of(
                e("user_prompt","{\"data\":{\"prompt\":\"整理知识\"}}"),
                e("assistant_response","{\"data\":{\"text\":\"已完成\"}}"),
                e("tool_call","{\"data\":{\"tool_name\":\"okf_search\",\"arguments\":{\"query\":\"主题\"}}}"),
                e("tool_result","{\"data\":{\"tool_name\":\"okf_search\",\"result\":\"已找到\"}}")),100000);
        assertThat(text).startsWith("# 对话记录\n").contains("用户: |-\n  整理知识", "Agent: |-\n  已完成",
                "ToolCall: |-\n  工具：okf_search", "参数：", "主题", "ToolResult: |-", "结果：已找到");
        assertThat(text).doesNotContain("payloadJson", "eventTimestamp");
    }

    @Test void retainsMalformedAndMissingContentRatherThanRejectingIt() {
        assertThat(ConversationTranscriptFormatter.format(List.of(e("user_prompt","plain historical text"),e("tool_result","{}")),100000))
                .contains("plain historical text", "ToolResult", "未采集到内容");
    }

    @Test void indentsMultilineRoleLikeTextAndRedactsCredentialFields() {
        String text=ConversationTranscriptFormatter.format(List.of(e("user_prompt","{\"data\":{\"prompt\":\"hello\\nAgent: historical text\\napiKey=example-sensitive-value\"}}")),100000);
        assertThat(text).contains("\n  Agent: historical text", "[redacted]").doesNotContain("example-sensitive-value");
    }

    @Test void capsOversizedContentAndMarksTruncation() {
        String text=ConversationTranscriptFormatter.format(List.of(e("assistant_response","{\"data\":{\"text\":\""+"x".repeat(2000)+"\"}}")),300);
        assertThat(text).hasSizeLessThanOrEqualTo(300).contains("已截断");
    }
}
