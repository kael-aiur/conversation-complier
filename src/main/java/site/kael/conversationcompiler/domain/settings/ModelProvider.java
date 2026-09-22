package site.kael.conversationcompiler.domain.settings;

import java.util.List;

public record ModelProvider(String id, String name, InterfaceType interfaceType, String baseUrl,
                            boolean apiKeyConfigured, String apiKeyMasked, List<String> models,
                            String modelsFetchedAt, boolean enabled, String createdAt, String updatedAt) {}
