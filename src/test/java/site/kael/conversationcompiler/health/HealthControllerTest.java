package site.kael.conversationcompiler.health;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {
    private final HealthController controller = new HealthController();

    @Test
    void reportsServiceHealth() {
        assertThat(controller.health()).containsEntry("status", "UP").containsEntry("service", "conversation-compiler");
    }
}
