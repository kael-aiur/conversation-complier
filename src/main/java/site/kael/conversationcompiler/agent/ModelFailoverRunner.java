package site.kael.conversationcompiler.agent;

import java.util.List;
import java.util.function.Function;

public class ModelFailoverRunner {
    public interface AttemptObserver { void started(String candidate, int attempt); void finished(String candidate, int attempt, String status, Throwable error); }
    public <T> T run(List<String> candidates, Function<String, T> invocation) { return run(candidates, invocation, new AttemptObserver(){ public void started(String c,int a){} public void finished(String c,int a,String s,Throwable e){} }); }
    public <T> T run(List<String> candidates, Function<String, T> invocation, AttemptObserver observer) {
        Throwable last = null;
        for (String candidate : candidates) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                observer.started(candidate, attempt);
                try { T result=invocation.apply(candidate); observer.finished(candidate,attempt,"completed",null); return result; }
                catch (RuntimeException error) {
                    observer.finished(candidate,attempt,"failed",error); last=error; Failure failure=classify(error);
                    if (!failure.retryable() || attempt == 2) { if (!failure.failover()) throw error; break; }
                    sleep(failure.delayMillis());
                }
            }
        }
        throw new IllegalStateException("all model candidates failed", last);
    }
    Failure classify(Throwable error) { String message=String.valueOf(error.getMessage()).toLowerCase(); if(message.contains("429")||message.contains("rate limit"))return new Failure(true,true,1000); if(message.contains("timeout")||message.contains("timed out")||message.contains("503")||message.contains("502")||message.contains("500"))return new Failure(true,true,500); if(message.contains("401")||message.contains("403"))return new Failure(true,false,0); return new Failure(false,false,0); }
    private void sleep(long millis){try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("retry interrupted",e);}}
    record Failure(boolean failover, boolean retryable, long delayMillis) {}
}
