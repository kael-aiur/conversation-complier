package site.kael.conversationcompiler.repository.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.domain.settings.*;

import java.time.Instant;
import java.util.*;

@Repository
public class JdbcKnowledgeSettingsRepository implements KnowledgeSettingsRepository {
    private final JdbcTemplate jdbc;
    public JdbcKnowledgeSettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<KnowledgeCompileSettings> find() {
        var settings = jdbc.query("SELECT s.*,p.name provider_name FROM knowledge_compile_settings s LEFT JOIN model_providers p ON p.id=s.provider_id WHERE s.id=1",
                (r,n) -> new KnowledgeCompileSettings(r.getString("provider_id"), r.getString("provider_name"),
                        r.getString("model_name"), findModels(), r.getInt("interval_minutes"), r.getString("prompt"),
                        r.getBoolean("enabled"), r.getString("updated_at")));
        return settings.stream().findFirst();
    }

    private List<KnowledgeModelSelection> findModels() {
        return jdbc.query("SELECT m.provider_id,p.name provider_name,m.model_name FROM knowledge_compile_setting_models m LEFT JOIN model_providers p ON p.id=m.provider_id WHERE m.setting_id=1 ORDER BY m.selection_order,m.id",
                (r,n) -> new KnowledgeModelSelection(r.getString("provider_id"), r.getString("provider_name"), r.getString("model_name")));
    }

    public void save(List<KnowledgeModelSelectionRequest> models, int interval, String prompt, boolean enabled) {
        if (models == null || models.isEmpty()) throw new IllegalArgumentException("at least one model is required");
        String now = Instant.now().toString();
        var first = models.get(0);
        jdbc.update("INSERT INTO knowledge_compile_settings(id,provider_id,model_name,interval_minutes,prompt,enabled,created_at,updated_at) VALUES(1,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET provider_id=excluded.provider_id,model_name=excluded.model_name,interval_minutes=excluded.interval_minutes,prompt=excluded.prompt,enabled=excluded.enabled,updated_at=excluded.updated_at",
                first.providerId(), first.modelName(), interval, prompt, enabled, now, now);
        jdbc.update("DELETE FROM knowledge_compile_setting_models WHERE setting_id=1");
        for (int i = 0; i < models.size(); i++) {
            var model = models.get(i);
            jdbc.update("INSERT INTO knowledge_compile_setting_models(setting_id,provider_id,model_name,selection_order) VALUES(1,?,?,?)",
                    model.providerId(), model.modelName(), i);
        }
    }
}
