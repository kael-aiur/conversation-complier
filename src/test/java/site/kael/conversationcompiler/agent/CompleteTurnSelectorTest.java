package site.kael.conversationcompiler.agent;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.domain.ConversationEvent;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CompleteTurnSelectorTest {
    private ConversationEvent e(int id, String type) { return new ConversationEvent(id,"s","e"+id,type,id,"now","{}"); }
    @Test void selectsOnlyCompleteTurnsAndLeavesIncompleteTailForNextRun() {
        var events=List.of(e(1,"user_prompt"),e(2,"tool_call"),e(3,"tool_result"),e(4,"assistant_response"),e(5,"user_prompt"),e(6,"assistant_response"),e(7,"user_prompt"),e(8,"tool_call"));
        var selected=CompleteTurnSelector.select(events,10,100000);
        assertThat(selected.turns()).isEqualTo(2);
        assertThat(selected.events()).hasSize(6);
    }
    @Test void respectsConfiguredTurnLimitAtCompleteBoundary() {
        var events=List.of(e(1,"user_prompt"),e(2,"assistant_response"),e(3,"user_prompt"),e(4,"assistant_response"));
        assertThat(CompleteTurnSelector.select(events,1,100000).events()).hasSize(2);
    }
    @Test void respectsCharacterLimitWithoutSplittingATurn() {
        var first=e(1,"user_prompt"); var oversized=new ConversationEvent(2,"s","e2","assistant_response",2,"now","x".repeat(500));
        assertThatThrownBy(() -> CompleteTurnSelector.select(List.of(first,oversized),10,200)).isInstanceOf(IllegalStateException.class).hasMessageContaining("no complete conversation turn");
    }
    @Test void rejectsUnfinishedAssistantTurnOrUnresolvedToolCall() {
        var safePrefix=CompleteTurnSelector.select(List.of(e(1,"user_prompt"),e(2,"assistant_response"),e(3,"user_prompt"),e(4,"tool_call")),10,100000);
        assertThat(safePrefix.events()).hasSize(2);
        assertThatThrownBy(() -> CompleteTurnSelector.select(List.of(e(1,"user_prompt"),e(2,"tool_call"),e(3,"assistant_response")),10,100000)).isInstanceOf(IllegalStateException.class);
    }
}
