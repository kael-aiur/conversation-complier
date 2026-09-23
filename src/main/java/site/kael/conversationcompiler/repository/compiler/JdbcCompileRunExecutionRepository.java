package site.kael.conversationcompiler.repository.compiler;

import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.agent.CompileResultRequest;
import java.time.Instant;

@Repository
public class JdbcCompileRunExecutionRepository implements CompileRunExecutionRepository {
 private final JdbcTemplate jdbc;
 private final ObjectMapper mapper;
 public JdbcCompileRunExecutionRepository(JdbcTemplate jdbc, ObjectMapper mapper){this.jdbc=jdbc; this.mapper=mapper;}
 public long startAttempt(long runId,int attemptNumber,String providerId,String modelName){String now=Instant.now().toString();jdbc.update("INSERT INTO compile_run_attempts(compile_run_id,attempt_number,provider_id,model_name,status,started_at) VALUES(?,?,?,? ,'running',?)",runId,attemptNumber,providerId,modelName,now);return jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);}
 public void finishAttempt(long attemptId,String status,String errorType,String errorMessage){jdbc.update("UPDATE compile_run_attempts SET status=?,error_type=?,error_message=?,finished_at=? WHERE id=?",status,errorType,errorMessage,Instant.now().toString(),attemptId);}
 public boolean markRunning(long id,String phase,String providerId,String modelName,String prompt){return jdbc.update("UPDATE compile_runs SET status='running', phase=?, progress=10, started_at=?, provider_id=?, model_name=?, prompt_snapshot=?, updated_at=? WHERE id=? AND status='pending'",phase,Instant.now().toString(),providerId,modelName,prompt,Instant.now().toString(),id)==1;}
 public void updatePhase(long id,String phase,int progress){jdbc.update("UPDATE compile_runs SET phase=?, progress=?, updated_at=? WHERE id=? AND status='running'",phase,Math.max(0,Math.min(progress,99)),Instant.now().toString(),id);}
 public boolean markCompleted(long id,CompileResultRequest result){String now=Instant.now().toString(); return jdbc.update("UPDATE compile_runs SET status=?, phase='completed', progress=100, summary=?, knowledge_count=?, result_json=?, finished_at=?, updated_at=? WHERE id=? AND status='running'",result.warnings()==null||result.warnings().isEmpty()?"completed":"completed_with_warnings",result.summary(),result.items().size(),json(result),now,now,id)==1;}
 public void markFailed(long id,String error){jdbc.update("UPDATE compile_runs SET status='failed', phase='failed', error_message=?, finished_at=?, updated_at=? WHERE id=? AND status='running'",error,Instant.now().toString(),Instant.now().toString(),id);}
 public void insertKnowledgeItems(long runId,CompileResultRequest result){String now=Instant.now().toString();for(var item:result.items()){jdbc.update("INSERT INTO compile_run_knowledge_items(compile_run_id,item_key,item_type,title,summary,action,status,confidence,content,wiki,slug,content_hash,created_at) SELECT ?,?,?,?,?,?,?,?,?,?,?,?,? WHERE EXISTS (SELECT 1 FROM compile_runs WHERE id=? AND status=\'running\')",runId,item.itemKey(),item.itemType(),item.title(),item.summary(),item.action(),item.status(),item.confidence(),null,item.wiki(),item.slug(),item.contentHash(),now,runId);}}
 private String json(CompileResultRequest result){ try { return mapper.writeValueAsString(result); } catch (JsonProcessingException e) { throw new IllegalStateException("cannot serialize compile result", e); } }
 public void advanceCompiledVersion(String sessionId,long version){jdbc.update("UPDATE conversations SET compiled_version=?, status='compiled', last_compiled_at=?, updated_at=? WHERE session_id=? AND compiled_version < ?",version,Instant.now().toString(),Instant.now().toString(),sessionId,version);}
}
