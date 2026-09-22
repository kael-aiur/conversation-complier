package site.kael.conversationcompiler.domain.settings;
import java.util.List;
public record ModelOptions(String providerId, String providerName, InterfaceType interfaceType, List<String> models) {}
