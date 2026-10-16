package com.webox.inventory;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import com.webox.shared.BusinessClock;
import com.webox.shared.ValueRules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
/** Typed public payloads prevent accidental persistence of personal order data. */
@Component
public class OutboxWriter {
    private final JdbcTemplate jdbc; private final JsonMapper mapper; private final BusinessClock clock;
    public OutboxWriter(JdbcTemplate jdbc,JsonMapper mapper,BusinessClock clock) { this.jdbc=jdbc;this.mapper=mapper;this.clock=clock; }
    public void inventoryUpdated(LocalDate date,UUID dishId,long remaining,long version,long revision) {
        ValueRules.safeInteger(remaining); ValueRules.safeInteger(version); ValueRules.safeInteger(revision);
        if (remaining>Integer.MAX_VALUE) throw new IllegalArgumentException("Stock exceeds the supported limit.");
        write("inventory.updated",date+"/"+dishId,Map.of("deliveryDate",date.toString(),"dishId",dishId.toString(),"remaining",remaining,"version",version,"menuRevision",revision,"occurredAt",clock.now().toString()));
    }
    public void menuChanged(LocalDate date,long revision) {
        ValueRules.safeInteger(revision);
        write("menu.changed",date.toString(),Map.of("deliveryDate",date.toString(),"menuRevision",revision));
    }
    private void write(String type,String aggregate,Map<String,Object> payload) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new IllegalStateException("Outbox writes require an active business write transaction.");
        Timestamp now=Timestamp.from(clock.now());
        jdbc.update("INSERT INTO outbox_events(id,event_type,aggregate_key,payload,created_at,next_attempt_at) VALUES (?,?,?,?,?,?)",
            UUID.randomUUID().toString(),type,aggregate,mapper.writeValueAsString(payload),now,now);
    }
}
