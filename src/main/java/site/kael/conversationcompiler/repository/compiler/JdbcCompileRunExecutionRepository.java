package site.kael.conversationcompiler.repository.compiler;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.kael.conversationcompiler.agent.CompileResultRequest;
import java.time.Instant;

@Repository
public class JdbcCompileRunExecutionRepository implements CompileRunExecutionRepository {
 private final JdbcTemplate jdbc; public JdbcCompileRunExecutionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public long startAttempt(long runId,int attemptNumber,String providerId,String modelName){String now=Instant.now().toString();jdbc.update("INSERT INTO compile_run_attempts(compile_run_id,attempt_number,provider_id,model_name,status,started_at) VALUES(?,?,?,? ,'running',?)",runId,attemptNumber,providerId,modelName,now);return jdbc.queryForObject("SELECT last_insert_rowid()",Long.class);}
 public void finishAttempt(long attemptId,String status,String errorType,String errorMessage){jdbc.update("UPDATE compile_run_attempts SET status=?,error_type=?,error_message=?,finished_at=? WHERE id=?",status,errorType,errorMessage,Instant.now().toString(),attemptId);}
 public boolean markRunning(long id,String phase,String providerId,String modelName,String prompt){return jdbc.update("UPDATE compile_runs SET status='running', phase=?, progress=10, started_at=?, provider_id=?, model_name=?, prompt_snapshot=?, updated_at=? WHERE id=? AND status='pending'",phase,Instant.now().toString(),providerId,modelName,prompt,Instant.now().toString(),id)==1;}
 public void markCompleted(long id,CompileResultRequest result){String now=Instant.now().toString();jdbc.update("UPDATE compile_runs SET status=?, phase='completed', progress=100, summary=?, knowledge_count=?, finished_at=?, updated_at=? WHERE id=?",result.warnings()==null||result.warnings().isEmpty()?"completed":"completed_with_warnings",result.summary(),result.items().size(),now,now,id);}
 public void markFailed(long id,String error){jdbc.update("UPDATE compile_runs SET status='failed', phase='failed', error_message=?, finished_at=?, updated_at=? WHERE id=?",error,Instant.now().toString(),Instant.now().toString(),id);}
 public void insertKnowledgeItems(long runId,CompileResultRequest result){String now=Instant.now().toString();for(var item:result.items()){jdbc.update("INSERT INTO compile_run_knowledge_items(compile_run_id,item_key,item_type,title,summary,action,status,confidence,content,wiki,slug,content_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",runId,item.itemKey(),item.itemType(),item.title(),item.summary(),item.action(),item.status(),item.confidence(),null,item.wiki(),item.slug(),item.contentHash(),now);}}
 public void advanceCompiledVersion(String sessionId,long version){jdbc.update("UPDATE conversations SET compiled_version=?, status='compiled', last_compiled_at=?, updated_at=? WHERE session_id=? AND compiled_version < ?",version,Instant.now().toString(),Instant.now().toString(),sessionId,version);}
}
