package com.webox.recommendation;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
/** One monotonic deadline covers queueing, both provider calls and every database recheck. */
public final class RequestDeadline implements AutoCloseable {
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"ai-deadlines");t.setDaemon(true);return t;});
    private final long started=System.nanoTime();private final java.time.Instant began=java.time.Instant.now();private final long ends;private final ScheduledFuture<?> timer;private final List<Runnable> cancellation=new ArrayList<>();private volatile boolean cancelled;
    long startedNanos(){return started;}java.time.Instant beganAt(){return began;}
    public RequestDeadline(Duration duration){long nanos=duration.toNanos();if(nanos<=0 || nanos>TimeUnit.SECONDS.toNanos(20))throw new IllegalArgumentException("Recommendation deadline must be within 20 seconds.");ends=System.nanoTime()+nanos;timer=TIMER.schedule(this::cancel,nanos,TimeUnit.NANOSECONDS);}
    public void check(){if(System.nanoTime()>=ends)throw AiFailure.timeout();if(cancelled || Thread.currentThread().isInterrupted())throw new CancellationException("Recommendation cancelled.");}
    public Duration remaining(){check();return Duration.ofNanos(Math.max(1,ends-System.nanoTime()));}
    public void onCancel(Runnable action){boolean run;synchronized(this){run=cancelled;if(!run)cancellation.add(action);}if(run)action.run();}
    public void track(AutoCloseable resource){onCancel(()->{try{resource.close();}catch(Exception ignored){}});}
    public void cancel(){List<Runnable> callbacks;synchronized(this){if(cancelled)return;cancelled=true;callbacks=List.copyOf(cancellation);cancellation.clear();}for(var callback:callbacks)try{callback.run();}catch(RuntimeException ignored){}}
    @Override public synchronized void close(){timer.cancel(false);cancellation.clear();}
}
