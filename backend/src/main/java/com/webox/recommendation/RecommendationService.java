package com.webox.recommendation;
import com.webox.auth.SessionValidity;
import com.webox.ordering.OrderPolicy;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.Meal;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Service;

@Service @ConditionalOnWebApplication
public class RecommendationService {
    private final OpenRouterConfiguration config;private final OpenRouterProvider provider;private final CandidateService candidates;private final IntentService intents;private final RecommendationValidator validator;private final RecommendationStreams streams;private final OrderPolicy policy;private final BusinessClock clock;private final SessionValidity sessions;
    private final ThreadPoolExecutor workers;private final Semaphore admission;private final RecommendationObservations observations;
    public RecommendationService(OpenRouterConfiguration config,OpenRouterProvider provider,CandidateService candidates,IntentService intents,RecommendationValidator validator,RecommendationStreams streams,OrderPolicy policy,BusinessClock clock,SessionValidity sessions,RecommendationObservations observations) {
        this.config=config;this.provider=provider;this.candidates=candidates;this.intents=intents;this.validator=validator;this.streams=streams;this.policy=policy;this.clock=clock;this.sessions=sessions;
        this.observations=observations;admission=new Semaphore(config.concurrent()+config.queue());
        workers=new ThreadPoolExecutor(config.concurrent(),config.concurrent(),0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(config.queue()),r->{var thread=new Thread(r,"recommendation-request");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    }
    @PreDestroy void stop(){workers.shutdownNow();}
    int inFlight(){return config.concurrent()+config.queue()-admission.availablePermits();}
    public void start(String user,LocalDate date,Meal meal,String prompt,HttpServletRequest request,HttpServletResponse response)throws IOException {
        policy.slot(date,meal);
        var deadline=new RequestDeadline(Duration.ofMillis(config.timeoutMillis()));Ticket ticket=null;boolean opened=false;
        try {
            if(!admission.tryAcquire())throw new ApiException(503,"AI_BUSY","Recommendations are busy. Please try again shortly.",true,Map.of());
            ticket=new Ticket(deadline);deadline.onCancel(ticket::cancel);
            var initial=candidates.prepare(user,date,IntentService.Intent.none());deadline.check();
            if(!initial.candidates().isEmpty() && !config.available())throw AiFailure.unavailable();
            var stream=streams.open(request,response,deadline,date);opened=true;
            var trace=observations.begin(stream.requestId(),config.model(),deadline);deadline.onCancel(()->{try{deadline.check();}catch(AiFailure timeout){trace.finish(timeout.code());}catch(CancellationException cancelled){trace.finish("CANCELLED");}});
            if(initial.candidates().isEmpty()){ticket.release();trace.finish("NO_MATCH");stream.done();return;}
            String session=request.getSession(false).getId();
            ticket.action=()->run(user,date,meal,prompt,session,deadline,stream,trace);
            try{ticket.submit();}catch(RejectedExecutionException full){ticket.release();stream.fail(new AiFailure("AI_BUSY","Recommendations are busy. Please try again shortly.",true));}
        }catch(AiFailure failure){throw new ApiException(503,failure.code(),failure.getMessage(),failure.retryable(),Map.of());}
        finally{if(!opened){if(ticket!=null)ticket.release();deadline.cancel();deadline.close();}}
    }
    private void run(String user,LocalDate date,Meal meal,String prompt,String session,RequestDeadline deadline,RecommendationStreams.Stream stream,RecommendationObservations.Trace trace) {
        try {
            deadline.check();if(!sessions.active(session)){stream.expired();return;}
            var extracted=provider.extract(prompt,clock.today(),deadline,trace::intent);
            var intent=intents.normalize(prompt,clock.today(),extracted.value());deadline.check();
            var prepared=candidates.prepare(user,date,intent);deadline.check();
            if(prepared.candidates().isEmpty()){trace.finish("NO_MATCH");stream.done();return;}
            var allowed=new HashSet<String>();prepared.candidates().forEach(c->allowed.add(c.dish().dishId()));var seen=new HashSet<String>();
            provider.recommend(prompt,intent,prepared,deadline,item->{
                deadline.check();if(!sessions.active(session)){stream.expired();throw new CancellationException();}
                try{policy.slot(date,meal);}catch(ApiException changed){throw AiFailure.clarification();}
                RecommendationValidator.validate(item,allowed,seen);
                var current=validator.current(user,date,intent,prepared,item);deadline.check();current.ifPresent(dish->{stream.card(dish,item.reason());trace.card();});
            },trace::recommendation);
            deadline.check();trace.finish(stream.count()==0?"NO_MATCH":"COMPLETE");stream.done();
        }catch(AiFailure failure){trace.finish(failure.code());stream.fail(failure);}
        catch(CancellationException cancelled){try{deadline.check();}catch(AiFailure timeout){stream.fail(timeout);}catch(CancellationException ignored){}}
        catch(RuntimeException unavailable){trace.finish("AI_UNAVAILABLE");stream.fail(AiFailure.unavailable());}
    }
    /** Queued cancellation removes the task; running cancellation frees capacity only after the worker exits. */
    private final class Ticket implements Runnable {
        private final RequestDeadline deadline;private Runnable action;private Thread running;private boolean released,cancelled;
        Ticket(RequestDeadline deadline){this.deadline=deadline;}
        synchronized void submit(){if(!cancelled && !released)workers.execute(this);}
        @Override public void run(){synchronized(this){if(cancelled || released)return;running=Thread.currentThread();}try{deadline.check();action.run();}catch(AiFailure | CancellationException ignored){}finally{synchronized(this){running=null;release();}Thread.interrupted();}}
        synchronized void cancel(){cancelled=true;if(running!=null)running.interrupt();else{workers.remove(this);release();}}
        synchronized void release(){if(released)return;released=true;admission.release();}
    }
}
