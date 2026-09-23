package site.kael.conversationcompiler.manager.settings;

import org.springframework.stereotype.Component;
import site.kael.conversationcompiler.common.*;
import site.kael.conversationcompiler.domain.settings.*;
import site.kael.conversationcompiler.infrastructure.crypto.ApiKeyEncryptionService;
import site.kael.conversationcompiler.infrastructure.provider.ProviderModelClient;
import site.kael.conversationcompiler.repository.settings.*;
import java.time.Instant; import java.util.*;

@Component
public class ModelProviderManager {
 private final ModelProviderRepository providers; private final KnowledgeSettingsRepository settings; private final ApiKeyEncryptionService crypto; private final ProviderModelClient client;
 public ModelProviderManager(ModelProviderRepository p,KnowledgeSettingsRepository s,ApiKeyEncryptionService c,ProviderModelClient client){providers=p;settings=s;crypto=c;this.client=client;}
 public List<ModelProvider> list(){return providers.findAll();}
 public ModelProvider get(String id){return providers.findById(id).orElseThrow(()->new NotFoundException("provider not found: "+id));}
 public List<String> fetch(FetchModelsRequest r){
  String key = r.apiKey();
  if (key == null || key.isBlank()) {
   if (r.providerId() == null || r.providerId().isBlank()) throw new BadRequestException("apiKey is required");
   if (!providers.exists(r.providerId())) throw new NotFoundException("provider not found: " + r.providerId());
   try { key = crypto.decrypt(providers.findEncryptedApiKey(r.providerId())); }
   catch (RuntimeException e) { throw new BadRequestException("saved API key cannot be decrypted; re-enter the API Key and try again"); }
  }
  return client.fetchModels(type(r.interfaceType()),required(r.baseUrl(),"baseUrl"),required(key,"apiKey"));
 }
 public ModelProvider save(String id,ModelProviderRequest r){InterfaceType type=type(r.interfaceType()); String name=required(r.name(),"name"),url=required(r.baseUrl(),"baseUrl"); List<String> models=r.models()==null?List.of():r.models().stream().filter(x->x!=null&&!x.isBlank()).distinct().toList(); if(models.isEmpty()||r.modelsFetchedAt()==null||r.modelsFetchedAt().isBlank())throw new BadRequestException("models must be fetched before saving"); String encrypted;String fp; if(r.apiKey()!=null&&!r.apiKey().isBlank()){encrypted=crypto.encrypt(r.apiKey());fp=crypto.fingerprint(r.apiKey());}else{if(id==null)throw new BadRequestException("apiKey is required");encrypted=providers.findEncryptedApiKey(id);fp=providers.findApiKeyFingerprint(id);} String providerId=id==null?"provider-"+UUID.randomUUID():id; providers.save(providerId,name,type,url,encrypted,fp,r.enabled()==null||r.enabled(),models,r.modelsFetchedAt());return get(providerId);}
 public void delete(String id){get(id); settings.find().filter(s->id.equals(s.providerId())).ifPresent(s->{throw new ConflictException("provider is used by knowledge settings");}); providers.delete(id);}
 public InterfaceType type(String value){try{return InterfaceType.valueOf(required(value,"interfaceType"));}catch(IllegalArgumentException e){throw new BadRequestException("unsupported interfaceType");}}
 private String required(String s,String f){if(s==null||s.isBlank())throw new BadRequestException(f+" is required");return s.trim();}
}
