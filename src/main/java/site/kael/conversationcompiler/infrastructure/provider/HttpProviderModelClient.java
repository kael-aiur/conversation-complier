package site.kael.conversationcompiler.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.common.BadRequestException;
import site.kael.conversationcompiler.domain.settings.InterfaceType;
import java.net.URI; import java.net.http.*; import java.time.Duration; import java.util.*;

@Component
public class HttpProviderModelClient implements ProviderModelClient {
 private final ObjectMapper mapper; private final HttpClient client;
 public HttpProviderModelClient(ObjectMapper mapper){this.mapper=mapper;this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();}
 public List<String> fetchModels(InterfaceType type,String baseUrl,String apiKey){
  String path=type==InterfaceType.anthropic?"/v1/models":"/models"; String url=baseUrl.replaceAll("/+$","")+path;
  try { var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("Authorization","Bearer "+apiKey); if(type==InterfaceType.anthropic)b.header("x-api-key",apiKey).header("anthropic-version","2023-06-01"); var r=client.send(b.GET().build(),HttpResponse.BodyHandlers.ofString()); if(r.statusCode()<200||r.statusCode()>=300)throw new BadRequestException("provider returned HTTP "+r.statusCode()); return parse(mapper.readTree(r.body())); }
  catch(BadRequestException e){throw e;} catch(Exception e){throw new BadRequestException("unable to fetch provider models: "+e.getMessage());}
 }
 private List<String> parse(JsonNode root){JsonNode list=root.path("data"); if(!list.isArray())list=root.path("models"); if(!list.isArray())throw new BadRequestException("provider response has no model list"); List<String> out=new ArrayList<>(); for(JsonNode n:list){String id=n.isTextual()?n.asText():n.path("id").asText(n.path("name").asText("")); if(!id.isBlank())out.add(id);} if(out.isEmpty())throw new BadRequestException("provider returned no models"); return out.stream().distinct().sorted().toList();}
}
