package com.webox.inventory;
import java.sql.Timestamp;
import java.time.*;
import java.util.concurrent.*;
import com.webox.shared.BusinessClock;
import jakarta.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
@Component @ConditionalOnWebApplication
public class OutboxDispatcher {
    private final JdbcTemplate jdbc;private final JsonMapper json;private final InventorySubscriptions subscriptions;private final BusinessClock clock;private final boolean enabled;private final long interval;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"inventory-outbox");t.setDaemon(true);return t;});
    public OutboxDispatcher(JdbcTemplate jdbc,JsonMapper json,InventorySubscriptions subscriptions,BusinessClock clock,@Value("${webox.inventory.dispatch-enabled:true}") boolean enabled,@Value("${INVENTORY_POLL_INTERVAL_MS:250}") long interval){if(interval<10)throw new IllegalArgumentException("Invalid inventory polling interval.");this.jdbc=jdbc;this.json=json;this.subscriptions=subscriptions;this.clock=clock;this.enabled=enabled;this.interval=interval;}
    private record Pending(String id,String type,String payload,int attempts) {}
    @PostConstruct void start(){if(enabled)worker.scheduleWithFixedDelay(()->{try{dispatch();}catch(RuntimeException ignored){/* Durable rows remain pending for the next poll. */}},interval,interval,TimeUnit.MILLISECONDS);}
    @PreDestroy void stop(){worker.shutdownNow();}
    public synchronized void dispatch() {
        var rows=jdbc.query("SELECT id,event_type,payload,attempt_count FROM outbox_events WHERE published_at IS NULL AND next_attempt_at<=? ORDER BY created_at,id LIMIT 100",(rs,n)->new Pending(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4)),Timestamp.from(clock.now()));
        for(var row:rows) {
            try {
                LocalDate date;
                if(row.type().equals("inventory.updated"))date=json.readValue(row.payload(),InventoryModels.Updated.class).deliveryDate();
                else if(row.type().equals("menu.changed"))date=json.readValue(row.payload(),InventoryModels.Changed.class).deliveryDate();
                else throw new IllegalArgumentException("Unsupported outbox event.");
                subscriptions.publish(date,row.type(),row.payload());markPublished(row.id());
            }catch(RuntimeException failure) {
                long delay=Math.min(30_000,250L*(1L<<Math.min(row.attempts(),7)));int attempts=row.attempts()==Integer.MAX_VALUE?Integer.MAX_VALUE:row.attempts()+1;
                jdbc.update("UPDATE outbox_events SET attempt_count=?,next_attempt_at=?,last_error_code='DISPATCH_FAILED' WHERE id=? AND published_at IS NULL",attempts,Timestamp.from(clock.now().plusMillis(delay)),row.id());
            }
        }
    }
    void markPublished(String id){jdbc.update("UPDATE outbox_events SET published_at=?,last_error_code=NULL WHERE id=? AND published_at IS NULL",Timestamp.from(clock.now()),id);}
}
