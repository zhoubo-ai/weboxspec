package com.webox.inventory;

import com.webox.auth.SessionValidity;
import com.webox.shared.ApiException;
import jakarta.annotation.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Single-instance fan-out. Business writers only append outbox; socket writes never run on their threads. */
@Component @ConditionalOnWebApplication
public class InventorySubscriptions {
    private final SessionValidity sessions;private final JsonMapper json;private final int perSession,maxConnections,capacity;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final long heartbeatNanos,lifetimeNanos,writeTimeoutNanos;
    private final Map<String,Connection> connections=new ConcurrentHashMap<>();
    private final ExecutorService writers;private final ScheduledExecutorService maintenance;
    private volatile boolean stopped;
    public InventorySubscriptions(SessionValidity sessions,JsonMapper json,io.micrometer.core.instrument.MeterRegistry metrics,
        @Value("${webox.inventory.heartbeat-ms:${SSE_HEARTBEAT_INTERVAL_MS:15000}}") long heartbeat,
        @Value("${webox.inventory.max-duration-ms:${SSE_MAX_DURATION_MS:1800000}}") long lifetime,
        @Value("${webox.inventory.max-per-session:${SSE_MAX_PER_SESSION:3}}") int perSession,
        @Value("${webox.inventory.max-connections:${SSE_MAX_CONNECTIONS:600}}") int maxConnections,
        @Value("${webox.inventory.queue-capacity:${SSE_QUEUE_CAPACITY:256}}") int capacity,
        @Value("${webox.inventory.write-timeout-ms:${SSE_WRITE_TIMEOUT_MS:5000}}") long timeout) {
        if(heartbeat<50 || lifetime<100 || perSession<1 || maxConnections<1 || capacity<2 || timeout<50)throw new IllegalArgumentException("Invalid inventory stream limits.");
        this.sessions=sessions;this.json=json;this.metrics=metrics;this.perSession=perSession;this.maxConnections=maxConnections;this.capacity=capacity;
        heartbeatNanos=TimeUnit.MILLISECONDS.toNanos(heartbeat);lifetimeNanos=TimeUnit.MILLISECONDS.toNanos(lifetime);writeTimeoutNanos=TimeUnit.MILLISECONDS.toNanos(timeout);
        writers=new ThreadPoolExecutor(8,8,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(maxConnections),r->{var t=new Thread(r,"inventory-writer");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        maintenance=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"inventory-maintenance");t.setDaemon(true);return t;});
    }
    @PostConstruct void start(){maintenance.scheduleWithFixedDelay(this::maintain,100,100,TimeUnit.MILLISECONDS);}
    public synchronized Connection register(LocalDate date,String sessionId) {
        var owned=connections.values().stream().filter(c->c.sessionId.equals(sessionId)).toList();
        if(stopped || connections.size()>=maxConnections || owned.size()>=perSession) {
            // A disconnected idle SSE socket is discovered on its next write. Probe this session's
            // bounded set now so reconnect need not wait for the regular 15-second heartbeat.
            if(!stopped)for(var existing:owned)existing.offer(": connection-check\n\n".getBytes(StandardCharsets.UTF_8));
            throw new ApiException(429,"RATE_LIMITED","Too many live inventory connections. Close another tab and try again.",true,Map.of());
        }
        var connection=new Connection(date,sessionId);connections.put(connection.id,connection);return connection;
    }
    public void publish(LocalDate date,String event,String data) {
        byte[] bytes=frame(event,data);for(var connection:connections.values())if(connection.date.equals(date))connection.offer(bytes);
    }
    static byte[] frame(String event,String data){return ("event: "+event+"\ndata: "+data+"\n\n").getBytes(StandardCharsets.UTF_8);}
    private void maintain() {
        long now=System.nanoTime();for(var connection:connections.values()) {
            try {
                if(now-connection.created>=lifetimeNanos){connection.close("lifetime");continue;}
                if(connection.stalled(now)){connection.close("write-timeout");continue;}
                if(now-connection.heartbeat>=heartbeatNanos){connection.heartbeat=now;connection.offer(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));}
            }catch(RuntimeException ignored){connection.close();}
        }
    }
    @PreDestroy void stop(){stopped=true;maintenance.shutdownNow();for(var connection:connections.values())connection.close();writers.shutdownNow();}
    public int size(){return connections.size();}
    public final class Connection {
        private final String id=UUID.randomUUID().toString(),sessionId;private final LocalDate date;
        private final long created=System.nanoTime();private volatile long heartbeat=created;
        private final ArrayDeque<byte[]> queue=new ArrayDeque<>();private final AtomicBoolean scheduled=new AtomicBoolean();
        private int queuedBytes;private boolean ready,closed,terminal,flushPending;private long pendingSince;
        private AsyncContext async;private ServletOutputStream output;
        private Connection(LocalDate date,String sessionId){this.date=date;this.sessionId=sessionId;}
        /** Register first, obtain a consistent DB snapshot, then prepend it to the buffered committed events. */
        public void begin(InventoryModels.Snapshot snapshot,AsyncContext async,HttpServletResponse response) throws IOException {
            byte[] bytes=frame("inventory.snapshot",json.writeValueAsString(snapshot));
            synchronized(this) {
                if(closed) {async.complete();return;}
                this.async=async;output=response.getOutputStream();queue.addFirst(bytes);queuedBytes+=bytes.length;ready=true;if(pendingSince==0)pendingSince=System.nanoTime();
                if(queuedBytes>1_048_576 || queue.size()>capacity){close("queue-limit");return;}
            }
            async.setTimeout(TimeUnit.NANOSECONDS.toMillis(lifetimeNanos));async.addListener(new AsyncListener() {
                @Override public void onComplete(AsyncEvent event){close();}
                @Override public void onTimeout(AsyncEvent event){close();}
                @Override public void onError(AsyncEvent event){close();}
                @Override public void onStartAsync(AsyncEvent event){}
            });
            output.setWriteListener(new WriteListener() {
                @Override public void onWritePossible(){kick();}
                @Override public void onError(Throwable failure){close();}
            });
            kick();
        }
        private void offer(byte[] bytes) {
            synchronized(this) {
                if(closed || terminal)return;
                if(queue.size()>=capacity || (long)queuedBytes+bytes.length>1_048_576){close("queue-limit");return;}
                queue.addLast(bytes);queuedBytes+=bytes.length;if(pendingSince==0)pendingSince=System.nanoTime();
            }kick();
        }
        private void kick() {
            synchronized(this){if(closed || !ready || output==null)return;}
            if(scheduled.compareAndSet(false,true))try{writers.execute(this::drain);}catch(RejectedExecutionException full){scheduled.set(false);close("executor-limit");}
        }
        private void drain() {
            boolean again=false;
            try {
                for(int count=0;count<4;count++) {
                    synchronized(this){if(closed || !ready)return;if(!output.isReady())return;}
                    boolean check; synchronized(this){check=!terminal && !queue.isEmpty();}
                    if(check && !sessions.active(sessionId)) {
                        synchronized(this){queue.clear();queuedBytes=0;terminal=true;var expired=frame("session.expired","{\"message\":\"Your session has expired. Please sign in again.\"}");queue.add(expired);queuedBytes=expired.length;}
                    }
                    synchronized(this) {
                        if(closed)return;if(!output.isReady())return;
                        // An offer may arrive after the empty-queue check above while a prior
                        // flush is pending. Re-enter authorization before writing those new bytes.
                        if(!check && !terminal && !queue.isEmpty())continue;
                        var bytes=queue.pollFirst();
                        if(bytes!=null){
                            queuedBytes-=bytes.length;
                            // One freshly authorized socket write can carry several complete SSE frames.
                            // Preserve every frame and its order; do not cache session validity or relax queue bounds.
                            var batch=new ByteArrayOutputStream(Math.min(65536,bytes.length+queuedBytes));batch.writeBytes(bytes);
                            for(int frames=1;frames<16 && !queue.isEmpty() && batch.size()+queue.peekFirst().length<=65536;frames++){
                                var next=queue.removeFirst();queuedBytes-=next.length;batch.writeBytes(next);
                            }
                            output.write(batch.toByteArray());flushPending=true;pendingSince=System.nanoTime();
                        }
                        if(flushPending && output.isReady()){output.flush();flushPending=false;}
                        if(queue.isEmpty() && !flushPending){pendingSince=0;if(terminal)close("session-expired");return;}
                        if(!output.isReady())return;
                    }
                }
                synchronized(this){again=!closed && (!queue.isEmpty() || flushPending) && output.isReady();}
            }catch(IOException | RuntimeException failure){close("write-or-session-error");}
            finally {
                scheduled.set(false);
                // Covers an offer arriving between the final empty-queue check and releasing this job.
                synchronized(this){if(!closed && ready && output!=null && (!queue.isEmpty() || flushPending)){try{again=output.isReady();}catch(RuntimeException ignored){close();}}}
                if(again)kick();
            }
        }
        private synchronized boolean stalled(long now){return pendingSince!=0 && now-pendingSince>=writeTimeoutNanos;}
        public void close() {close("completed");}
        private void close(String reason) {
            AsyncContext context;
            synchronized(this){if(closed)return;closed=true;queue.clear();queuedBytes=0;context=async;}
            connections.remove(id,this);
            metrics.counter("webox.inventory.closed","reason",reason).increment();
            if(context!=null)try{context.complete();}catch(IllegalStateException ignored){}
        }
    }
}
