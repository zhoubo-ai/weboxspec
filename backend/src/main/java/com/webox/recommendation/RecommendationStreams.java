package com.webox.recommendation;
import com.webox.auth.SessionValidity;
import com.webox.catalog.MenuModels.MenuDish;
import com.webox.shared.ApiException;
import jakarta.annotation.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Bounded non-blocking downstream transport, independent of provider and database worker threads. */
@Component @ConditionalOnWebApplication
public class RecommendationStreams {
    private final JsonMapper json;private final SessionValidity sessions;private final int limit;
    private final Map<String,Stream> active=new ConcurrentHashMap<>();
    private final ExecutorService writers;private final ScheduledExecutorService maintenance;
    public RecommendationStreams(JsonMapper json,SessionValidity sessions,OpenRouterConfiguration config) {
        this.json=json;this.sessions=sessions;limit=2*(config.concurrent()+config.queue());
        writers=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(limit),r->{var t=new Thread(r,"recommendation-writer");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
        maintenance=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"recommendation-connections");t.setDaemon(true);return t;});
    }
    public int size(){return active.size();}
    @PostConstruct void start(){maintenance.scheduleWithFixedDelay(()->{for(var stream:active.values())stream.maintain();},100,100,TimeUnit.MILLISECONDS);}
    @PreDestroy void stop(){maintenance.shutdownNow();active.values().forEach(Stream::close);writers.shutdownNow();}
    public synchronized Stream open(HttpServletRequest request,HttpServletResponse response,RequestDeadline deadline,LocalDate date) throws IOException {
        if(active.size()>=limit)throw new ApiException(503,"AI_BUSY","Recommendations are busy. Please try again shortly.",true,Map.of());
        var stream=new Stream(request.getSession(false).getId(),deadline,date);active.put(stream.id,stream);
        try {
            response.setContentType("text/event-stream");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");response.setHeader("X-Accel-Buffering","no");
            stream.begin(request.startAsync(),response);return stream;
        }catch(IOException | RuntimeException failure){stream.close();throw failure;}
    }
    private record Frame(String event,byte[] bytes){}
    public final class Stream {
        private final String id=UUID.randomUUID().toString(),session;private final LocalDate date;private final RequestDeadline deadline;
        private final ArrayDeque<Frame> queue=new ArrayDeque<>();private final AtomicBoolean scheduled=new AtomicBoolean();private final long created=System.nanoTime();
        private long pendingSince,heartbeat=created,probe=created;private int bytes,cards,sentCards;private boolean closed,terminal,flushPending,metaSent;private volatile boolean sessionEnded;
        private AsyncContext async;private ServletOutputStream output;
        Stream(String session,RequestDeadline deadline,LocalDate date){this.session=session;this.deadline=deadline;this.date=date;}
        public String requestId(){return id;}public synchronized int count(){return cards;}
        private Frame frame(String event,Object value){return new Frame(event,("event: "+event+"\ndata: "+json.writeValueAsString(value)+"\n\n").getBytes(StandardCharsets.UTF_8));}
        private Frame meta(){return frame("meta",Map.of("requestId",id,"deliveryDate",date));}
        void begin(AsyncContext context,HttpServletResponse response)throws IOException {
            synchronized(this){async=context;output=response.getOutputStream();append(meta());}
            context.setTimeout(25_000);context.addListener(new AsyncListener(){public void onComplete(AsyncEvent e){close();}public void onTimeout(AsyncEvent e){close();}public void onError(AsyncEvent e){close();}public void onStartAsync(AsyncEvent e){}});
            output.setWriteListener(new WriteListener(){public void onWritePossible(){kick();}public void onError(Throwable e){close();}});
            deadline.onCancel(()->{try{deadline.check();}catch(AiFailure timeout){fail(timeout);}catch(CancellationException ignored){}});kick();
        }
        private void append(Frame frame) {
            if(queue.size()>=8 || (long)bytes+frame.bytes().length>16_777_216){close();return;}queue.add(frame);bytes+=frame.bytes().length;if(pendingSince==0)pendingSince=System.nanoTime();
        }
        public void card(MenuDish dish,String reason) {
            deadline.check();synchronized(this){if(closed || terminal)throw new CancellationException("Recommendation ended.");if(cards>=3)throw AiFailure.invalid();append(frame("card",Map.of("requestId",id,"index",cards,"dish",dish,"reason",reason)));cards++;}kick();
        }
        public void done() {synchronized(this){if(closed || terminal)return;terminal=true;append(frame("done",Map.of("requestId",id,"count",cards,"outcome",cards==0?"NO_MATCH":"COMPLETE")));}kick();}
        public void fail(AiFailure failure) {synchronized(this){if(closed || terminal)return;terminal=true;append(frame("error",Map.of("requestId",id,"code",failure.code(),"message",failure.getMessage(),"retryable",failure.retryable(),"partial",cards>0)));}kick();}
        public void expired() {
            synchronized(this){if(closed || sessionEnded)return;sessionEnded=true;terminal=true;queue.clear();bytes=0;cards=sentCards;if(!metaSent)append(meta());append(frame("error",Map.of("requestId",id,"code","SESSION_EXPIRED","message","Your session has expired. Please sign in again.","retryable",false,"partial",sentCards>0)));}deadline.cancel();
        }
        private void maintain() {
            try {
                synchronized(this){long now=System.nanoTime();if(closed)return;if(now-created>TimeUnit.SECONDS.toNanos(25) || pendingSince!=0 && now-pendingSince>TimeUnit.SECONDS.toNanos(5)){close();return;}if(!terminal && now-heartbeat>=TimeUnit.SECONDS.toNanos(15)){heartbeat=now;append(new Frame("",": keep-alive\n\n".getBytes(StandardCharsets.UTF_8)));}}
                // Short requests need to discover a disconnected browser before the 15-second heartbeat.
                synchronized(this){long now=System.nanoTime();if(!closed && !terminal && now-probe>=TimeUnit.SECONDS.toNanos(1)){probe=now;append(new Frame("",": connection-check\n\n".getBytes(StandardCharsets.UTF_8)));}}
                kick();
            }catch(RuntimeException failure){close();}
        }
        private void kick() {
            synchronized(this){if(closed || output==null || queue.isEmpty() && !flushPending)return;}
            if(scheduled.compareAndSet(false,true))try{writers.execute(this::drain);}catch(RejectedExecutionException full){scheduled.set(false);close();}
        }
        private void drain() {
            boolean again=false;
            try {
                for(int i=0;i<8;i++) {
                    synchronized(this){if(closed || !output.isReady())return;}
                    if(!sessionEnded && !sessions.active(session))expired();
                    synchronized(this) {
                        if(closed || !output.isReady())return;var frame=queue.poll();
                        if(frame!=null){bytes-=frame.bytes().length;output.write(frame.bytes());flushPending=true;pendingSince=System.nanoTime();if(frame.event().equals("meta"))metaSent=true;if(frame.event().equals("card"))sentCards++;}
                        if(flushPending && output.isReady()){output.flush();flushPending=false;}
                        if(queue.isEmpty() && !flushPending){pendingSince=0;if(terminal)close();return;}
                    }
                }
            }catch(IOException | RuntimeException failure){close();}
            finally {scheduled.set(false);synchronized(this){if(!closed && (!queue.isEmpty() || flushPending))try{again=output.isReady();}catch(RuntimeException failure){close();}}if(again)kick();}
        }
        public void close(){AsyncContext context;synchronized(this){if(closed)return;closed=true;queue.clear();bytes=0;context=async;}active.remove(id,this);deadline.cancel();deadline.close();if(context!=null)try{context.complete();}catch(IllegalStateException ignored){}}
    }
}
