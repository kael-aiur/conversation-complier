package site.kael.conversationcompiler.agent;

import org.junit.jupiter.api.Test;
import site.kael.conversationcompiler.domain.ConversationEvent;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ConversationBatchSelectorTest {
    private ConversationEvent e(int id, String type) { return new ConversationEvent(id,"s","e"+id,type,id,"now","{}"); }

    @Test void includesMissingAssistantAndToolResultWithoutRejectingLaterRounds() {
        var events = List.of(e(1,"session_start"),e(2,"user_prompt"),e(3,"tool_call"),
                e(4,"user_prompt"),e(5,"assistant_response"),e(6,"user_prompt"),e(7,"tool_result"));
        var selected = ConversationBatchSelector.select(events,20,100000);
        assertThat(selected.events()).containsExactlyElementsOf(events);
        assertThat(selected.turns()).isEqualTo(3);
        assertThat(selected.lastIndex()).isEqualTo(6);
    }

    @Test void limitsUserRoundsEvenWhenNeitherRoundHasAssistantAnswer() {
        var events = List.of(e(1,"user_prompt"),e(2,"tool_call"),e(3,"user_prompt"),e(4,"tool_result"));
        var selected = ConversationBatchSelector.select(events,1,100000);
        assertThat(selected.events()).containsExactly(events.get(0),events.get(1));
        assertThat(selected.lastIndex()).isEqualTo(1);
    }

    @Test void acceptsLeadingFragmentsWithoutAnyUserMessage() {
        var events=List.of(e(1,"tool_result"),e(2,"assistant_response"));
        assertThat(ConversationBatchSelector.select(events,1,100000).events()).containsExactlyElementsOf(events);
    }

    @Test void leavesNextWholeRoundWhenItsContentWouldExceedLimit() {
        var events=List.of(e(1,"user_prompt"),e(2,"assistant_response"),e(3,"user_prompt"),e(4,"assistant_response"));
        int firstChars=ConversationTranscriptFormatter.HEADER.length()+ConversationTranscriptFormatter.formatEvent(events.get(0)).length()
                +ConversationTranscriptFormatter.formatEvent(events.get(1)).length();
        assertThat(ConversationBatchSelector.select(events,20,firstChars+10).events()).hasSize(2);
    }

    @Test void oversizedFirstRoundCanBeSplitWithoutBlockingProgress() {
        var large = new ConversationEvent(1,"s","e1","user_prompt",1,"now","{\"data\":{\"prompt\":\""+"x".repeat(1000)+"\"}}");
        var events=List.of(large,e(2,"tool_call"),e(3,"assistant_response"));
        var selected=ConversationBatchSelector.select(events,20,300);
        assertThat(selected.events()).containsExactly(large);
        assertThat(selected.lastIndex()).isZero();
        assertThat(ConversationTranscriptFormatter.format(selected.events(),300)).hasSizeLessThanOrEqualTo(300).contains("已截断");
        assertThat(ConversationBatchSelector.select(events.subList(1,3),20,300).events()).hasSize(2);
    }

    @Test void rejectsOnlyEmptyInputOrInvalidLimits() {
        assertThatThrownBy(() -> ConversationBatchSelector.select(List.of(),20,200000)).hasMessageContaining("no collected");
        assertThatThrownBy(() -> ConversationBatchSelector.select(List.of(e(1,"user_prompt")),0,10)).isInstanceOf(IllegalArgumentException.class);
    }
}
