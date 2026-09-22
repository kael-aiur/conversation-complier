package site.kael.conversationcompiler.manager.settings;
import org.junit.jupiter.api.Test; import site.kael.conversationcompiler.domain.settings.*; import site.kael.conversationcompiler.infrastructure.crypto.ApiKeyEncryptionService; import site.kael.conversationcompiler.infrastructure.provider.ProviderModelClient; import site.kael.conversationcompiler.repository.settings.*; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;
class ModelProviderManagerTest {
 @Test void fetchesModelsThroughProviderClient(){var p=mock(ModelProviderRepository.class);var s=mock(KnowledgeSettingsRepository.class);var c=mock(ProviderModelClient.class);when(c.fetchModels(InterfaceType.responses,"https://x","key")).thenReturn(List.of("m"));assertThat(new ModelProviderManager(p,s,new ApiKeyEncryptionService("secret"),c).fetch(new FetchModelsRequest("responses","https://x","key"))).containsExactly("m");}
 @Test void refusesSaveWithoutFetchedModels(){var m=new ModelProviderManager(mock(ModelProviderRepository.class),mock(KnowledgeSettingsRepository.class),new ApiKeyEncryptionService("secret"),mock(ProviderModelClient.class));assertThatThrownBy(()->m.save(null,new ModelProviderRequest("P","responses","url","key",List.of(),null,true))).isInstanceOf(RuntimeException.class);}
}
