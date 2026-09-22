package site.kael.conversationcompiler.repository.settings;

import site.kael.conversationcompiler.domain.settings.KnowledgeCompileSettings;
import java.util.Optional;

public interface KnowledgeSettingsRepository { Optional<KnowledgeCompileSettings> find(); void save(String providerId, String modelName, int interval, String prompt, boolean enabled); }
