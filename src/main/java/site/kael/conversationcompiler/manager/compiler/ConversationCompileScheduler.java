package site.kael.conversationcompiler.manager.compiler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.domain.compiler.CompileTriggerType;
import site.kael.conversationcompiler.repository.ConversationRepository;
import site.kael.conversationcompiler.repository.compiler.CompileRunRepository;
import site.kael.conversationcompiler.repository.settings.KnowledgeSettingsRepository;

@Component
@ConditionalOnProperty(name={"conversation-compiler.compiler.enabled", "conversation-compiler.agent.enabled"}, havingValue="true")
public class ConversationCompileScheduler {
    private final ConversationRepository conversations;
    private final CompileRunRepository runs;
    private final KnowledgeSettingsRepository settings;
    public ConversationCompileScheduler(ConversationRepository conversations, CompileRunRepository runs, KnowledgeSettingsRepository settings) { this.conversations=conversations; this.runs=runs; this.settings=settings; }
    @Scheduled(fixedDelayString="${conversation-compiler.compiler.scan-interval-seconds:60}000")
    public void scanIdleConversations() {
        var config=settings.find().orElse(null);
        if (config == null || !config.enabled() || config.intervalMinutes() < 1 || config.providerId() == null || config.modelName() == null) return;
        double cutoff = System.currentTimeMillis() / 1000.0 - config.intervalMinutes() * 60.0;
        conversations.findIdleCandidates(cutoff, 20).forEach(conversation -> {
            if (!runs.hasActiveRun(conversation.sessionId())) {
                long from = conversation.compiledVersion() + 1;
                try { runs.createPending(conversation.sessionId(), from, conversation.version(), 0, 0, conversation.version() - conversation.compiledVersion(), CompileTriggerType.scheduler); }
                catch (org.springframework.dao.DataIntegrityViolationException ignored) { /* another scheduler won the claim */ }
            }
        });
    }
}
