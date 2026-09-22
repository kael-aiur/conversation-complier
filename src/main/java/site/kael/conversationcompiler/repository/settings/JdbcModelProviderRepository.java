package site.kael.conversationcompiler.repository.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.domain.settings.*;
import java.time.Instant; import java.util.*;

@Repository
public class JdbcModelProviderRepository implements ModelProviderRepository {
 private final JdbcTemplate jdbc; public JdbcModelProviderRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<ModelProvider> findAll(){return jdbc.query("SELECT * FROM model_providers ORDER BY name",(r,n)->map(r.getString("id"),r.getString("name"),InterfaceType.valueOf(r.getString("interface_type")),r.getString("base_url"),r.getString("api_key_fingerprint"),r.getBoolean("enabled"),r.getString("created_at"),r.getString("updated_at")));}
 public Optional<ModelProvider> findById(String id){return findAll().stream().filter(x->x.id().equals(id)).findFirst();}
 public boolean exists(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM model_providers WHERE id=?",Integer.class,id)>0;}
 public void save(String id,String name,InterfaceType type,String baseUrl,String key,String fp,boolean enabled,List<String> models,String fetchedAt){String now=Instant.now().toString(); jdbc.update("INSERT INTO model_providers(id,name,interface_type,base_url,api_key_ciphertext,api_key_fingerprint,enabled,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,interface_type=excluded.interface_type,base_url=excluded.base_url,api_key_ciphertext=excluded.api_key_ciphertext,api_key_fingerprint=excluded.api_key_fingerprint,enabled=excluded.enabled,updated_at=excluded.updated_at",id,name,type.name(),baseUrl,key,fp,enabled,now,now); jdbc.update("DELETE FROM provider_models WHERE provider_id=?",id); for(String model:models) jdbc.update("INSERT INTO provider_models(provider_id,model_name,fetched_at) VALUES(?,?,?)",id,model,fetchedAt==null?now:fetchedAt);}
 public String findEncryptedApiKey(String id){return jdbc.queryForObject("SELECT api_key_ciphertext FROM model_providers WHERE id=?",String.class,id);}
 public String findApiKeyFingerprint(String id){return jdbc.queryForObject("SELECT api_key_fingerprint FROM model_providers WHERE id=?",String.class,id);}
 public void delete(String id){jdbc.update("DELETE FROM model_providers WHERE id=?",id);}
 public List<String> findModels(String id){return jdbc.queryForList("SELECT model_name FROM provider_models WHERE provider_id=? AND enabled=1 ORDER BY model_name",String.class,id);}
 private ModelProvider map(String id,String name,InterfaceType type,String url,String fp,boolean enabled,String created,String updated){String masked=fp==null?null:"••••••••";return new ModelProvider(id,name,type,url,fp!=null,masked,findModels(id),jdbc.queryForObject("SELECT MAX(fetched_at) FROM provider_models WHERE provider_id=?",String.class,id),enabled,created,updated);}
}
