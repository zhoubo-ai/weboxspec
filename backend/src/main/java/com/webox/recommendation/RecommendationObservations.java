package com.webox.recommendation;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
/** Bounded diagnostic evidence. Never stores user IDs, prompts, sessions, secrets or upstream messages. */
@Component
public final class RecommendationObservations {
    private final com.webox.shared.OperationalMetrics metrics;
    public RecommendationObservations(com.webox.shared.OperationalMetrics metrics){this.metrics=metrics;}
    private final LinkedHashMap<String,Trace> traces=new LinkedHashMap<>();
    public synchronized Trace begin(String requestId,String configuredModel,RequestDeadline deadline){var trace=new Trace(requestId,configuredModel,deadline,metrics);traces.put(requestId,trace);while(traces.size()>128)traces.remove(traces.keySet().iterator().next());return trace;}
    public synchronized Optional<Snapshot> find(String requestId){var trace=traces.get(requestId);return trace==null?Optional.empty():Optional.of(trace.snapshot());}
    public record Snapshot(String requestId,String configuredModel,Instant beganAt,OpenRouterProvider.Metadata intent,OpenRouterProvider.Metadata recommendation,Long firstCardMillis,Long finishedMillis,int cards,String outcome){}
    public static final class Trace {
        private final com.webox.shared.OperationalMetrics metrics;private final String id,model;private final Instant began;private final long start;private OpenRouterProvider.Metadata intent,recommendation;private Long first,finished;private int cards;private String outcome="IN_PROGRESS";
        Trace(String id,String model,RequestDeadline deadline,com.webox.shared.OperationalMetrics metrics){this.metrics=metrics;this.id=id;this.model=model;this.began=deadline.beganAt();this.start=deadline.startedNanos();}
        public synchronized void intent(OpenRouterProvider.Metadata metadata){intent=metadata;}
        public synchronized void recommendation(OpenRouterProvider.Metadata metadata){recommendation=metadata;}
        public synchronized void card(){if(first==null){long elapsed=System.nanoTime()-start;first=elapsed/1_000_000;metrics.firstCard(elapsed);}cards++;}
        public synchronized void finish(String result){if(finished==null){long elapsed=System.nanoTime()-start;finished=elapsed/1_000_000;outcome=result;metrics.aiFinished(result,elapsed);}}
        synchronized Snapshot snapshot(){return new Snapshot(id,model,began,intent,recommendation,first,finished,cards,outcome);}
    }
}
