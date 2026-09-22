package site.kael.conversationcompiler.manager.settings;
import org.junit.jupiter.api.Test; import site.kael.conversationcompiler.domain.settings.*; import site.kael.conversationcompiler.repository.settings.*; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;
class KnowledgeSettingsManagerTest {
 @Test void savesValidSettings(){var s=mock(KnowledgeSettingsRepository.class);var p=mock(ModelProviderRepository.class);var provider=new ModelProvider("p","OpenAI",InterfaceType.responses,"url",true,"••••abcd",List.of("gpt"),"now",true,""," ");when(p.exists("p")).thenReturn(true);when(p.findById("p")).thenReturn(Optional.of(provider));when(p.findModels("p")).thenReturn(List.of("gpt"));new KnowledgeSettingsManager(s,p).save(new KnowledgeSettingsRequest("p","gpt",30,"prompt",true));verify(s).save("p","gpt",30,"prompt",true);}
 @Test void rejectsUnavailableModel(){var s=mock(KnowledgeSettingsRepository.class);var p=mock(ModelProviderRepository.class);when(p.exists("p")).thenReturn(true);when(p.findById("p")).thenReturn(Optional.of(new ModelProvider("p","P",InterfaceType.responses,"u",true,"x",List.of(),"",true,"","")));when(p.findModels("p")).thenReturn(List.of());assertThatThrownBy(()->new KnowledgeSettingsManager(s,p).save(new KnowledgeSettingsRequest("p","bad",1,"",true))).isInstanceOf(RuntimeException.class);}
}
