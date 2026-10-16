package com.webox.shared;
import com.webox.inventory.InventorySubscriptions;
import com.webox.recommendation.RecommendationStreams;
import io.micrometer.core.instrument.*;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/** Aggregate operational metrics only: no account, session, address, prompt or generation-ID labels. */
@Component
public class OperationalMetrics implements SmartInitializingSingleton {
    private final MeterRegistry registry;private final JdbcTemplate jdbc;private final ObjectProvider<InventorySubscriptions> inventory;private final ObjectProvider<RecommendationStreams> recommendations;
    public OperationalMetrics(MeterRegistry registry,JdbcTemplate jdbc,ObjectProvider<InventorySubscriptions> inventory,ObjectProvider<RecommendationStreams> recommendations){this.registry=registry;this.jdbc=jdbc;this.inventory=inventory;this.recommendations=recommendations;}
    @Override public void afterSingletonsInstantiated(){
        var stock=inventory.getIfAvailable();if(stock!=null)Gauge.builder("webox.inventory.active",stock,InventorySubscriptions::size).register(registry);
        var ai=recommendations.getIfAvailable();if(ai!=null)Gauge.builder("webox.ai.active",ai,RecommendationStreams::size).register(registry);
        Gauge.builder("webox.outbox.pending",jdbc,db->{try{return db.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL",Long.class);}catch(RuntimeException unavailable){return -1;}}).register(registry);
        for(String outcome:Set.of("created","replayed"))registry.counter("webox.orders", "outcome",outcome);
        registry.counter("webox.order.lock.retries");
    }
    public void order(boolean replay){registry.counter("webox.orders","outcome",replay?"replayed":"created").increment();}
    public void lockRetry(){registry.counter("webox.order.lock.retries").increment();}
    public void failure(String code){
        if(Set.of("INSUFFICIENT_STOCK","DISH_UNAVAILABLE","DISH_CHANGED","ACTIVE_ORDER_EXISTS","VERSION_CONFLICT","IDEMPOTENCY_KEY_REUSED","QUOTE_CONSUMED","INVALID_ORDER_TRANSITION").contains(code))registry.counter("webox.conflicts","code",code).increment();
    }
    public void limited(String scope){registry.counter("webox.rate_limited","scope",scope).increment();}
    public void firstCard(long nanos){Timer.builder("webox.ai.first_card").publishPercentileHistogram().register(registry).record(Duration.ofNanos(nanos));}
    public void aiFinished(String outcome,long nanos){String label=Set.of("COMPLETE","NO_MATCH","CANCELLED","AI_INVALID_RESPONSE","AI_TIMEOUT","AI_UNAVAILABLE","AI_BUSY","INTENT_CLARIFICATION_REQUIRED","SESSION_EXPIRED").contains(outcome)?outcome:"OTHER";Timer.builder("webox.ai.finished").tag("outcome",label).publishPercentileHistogram().register(registry).record(Duration.ofNanos(nanos));}
}
