package site.kael.conversationcompiler.controller.settings;
import org.junit.jupiter.api.Test; import org.springframework.test.web.servlet.MockMvc; import org.springframework.test.web.servlet.setup.MockMvcBuilders; import site.kael.conversationcompiler.domain.settings.*; import site.kael.conversationcompiler.service.settings.ModelProviderService; import java.util.*;
import static org.mockito.Mockito.*; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ModelProviderControllerTest { private final ModelProviderService service=mock(ModelProviderService.class); private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new ModelProviderController(service)).build();
 @Test void fetchesModels() throws Exception {when(service.fetch(any())).thenReturn(List.of("gpt"));mvc.perform(post("/api/v1/model-providers/fetch-models").contentType("application/json").content("{\"interfaceType\":\"responses\",\"baseUrl\":\"https://x\",\"apiKey\":\"key\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.models[0]").value("gpt"));}
 @Test void deletesProvider() throws Exception {mvc.perform(delete("/api/v1/model-providers/p")).andExpect(status().isNoContent());verify(service).delete("p");}
}
