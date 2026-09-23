package site.kael.conversationcompiler.manager.settings;

import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.common.BadRequestException;
import site.kael.conversationcompiler.domain.settings.*;
import site.kael.conversationcompiler.repository.settings.*;

import java.util.*;

@Component
public class KnowledgeSettingsManager {
    private final KnowledgeSettingsRepository settings;
    private final ModelProviderRepository providers;
    public KnowledgeSettingsManager(KnowledgeSettingsRepository s, ModelProviderRepository p) { settings=s; providers=p; }
    public Optional<KnowledgeCompileSettings> get() { return settings.find(); }

    public void save(KnowledgeSettingsRequest request) {
        if (request.intervalMinutes() == null || request.intervalMinutes() < 1) {
            throw new BadRequestException("intervalMinutes must be a positive integer");
        }
        List<KnowledgeModelSelectionRequest> models = normalizeModels(request);
        if (models.isEmpty()) throw new BadRequestException("at least one model is required");
        Set<String> seen = new HashSet<>();
        for (var model : models) {
            if (model == null || model.providerId() == null || model.modelName() == null
                    || model.providerId().isBlank() || model.modelName().isBlank()) {
                throw new BadRequestException("each model must include providerId and modelName");
            }
            String key = model.providerId() + "\n" + model.modelName();
            if (!seen.add(key)) throw new BadRequestException("duplicate model selection");
            if (!providers.exists(model.providerId())) throw new BadRequestException("provider not found: " + model.providerId());
            var provider = providers.findById(model.providerId()).orElseThrow();
            if (!provider.enabled()) throw new BadRequestException("provider is disabled: " + model.providerId());
            if (!providers.findModels(model.providerId()).contains(model.modelName())) {
                throw new BadRequestException("model is not available for provider: " + model.modelName());
            }
        }
        String prompt = request.prompt() == null ? "" : request.prompt();
        boolean enabled = request.enabled() == null || request.enabled();
        if (models.size() == 1) {
            var model = models.get(0);
            settings.save(model.providerId(), model.modelName(), request.intervalMinutes(), prompt, enabled);
        } else {
            settings.save(models, request.intervalMinutes(), prompt, enabled);
        }
    }

    private List<KnowledgeModelSelectionRequest> normalizeModels(KnowledgeSettingsRequest request) {
        if (request.models() != null && !request.models().isEmpty()) return request.models();
        if (request.providerId() == null || request.modelName() == null) return List.of();
        return List.of(new KnowledgeModelSelectionRequest(request.providerId(), request.modelName()));
    }

    public List<ModelOptions> options() {
        return providers.findAll().stream().filter(ModelProvider::enabled)
                .map(p -> new ModelOptions(p.id(), p.name(), p.interfaceType(), p.models())).toList();
    }
}
