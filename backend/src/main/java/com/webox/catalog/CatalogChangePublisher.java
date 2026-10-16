package com.webox.catalog;
import java.time.LocalDate;
import java.util.*;
import com.webox.inventory.OutboxWriter;
import com.webox.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Component
public class CatalogChangePublisher {
    private final JdbcTemplate jdbc;private final OutboxWriter outbox;private final MenuDetailsCache cache;private final BusinessClock clock;
    public CatalogChangePublisher(JdbcTemplate jdbc,OutboxWriter outbox,MenuDetailsCache cache,BusinessClock clock){this.jdbc=jdbc;this.outbox=outbox;this.cache=cache;this.clock=clock;}
    /** Caller already holds the dish root. Daily-menu writers acquire that root before membership changes. */
    public void changed(String dish,String actor) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())throw new IllegalStateException("Catalog changes require a write transaction.");
        var dates=jdbc.query("SELECT delivery_date FROM daily_supply WHERE dish_id=? ORDER BY delivery_date",(rs,n)->rs.getDate(1).toLocalDate(),dish);var revisions=new LinkedHashMap<LocalDate,Long>();
        for(var date:dates)revisions.put(date,jdbc.queryForObject("SELECT revision FROM daily_menus WHERE delivery_date=? FOR UPDATE",Long.class,date));
        for(var entry:revisions.entrySet()) {
            LocalDate date=entry.getKey();jdbc.queryForList("SELECT version FROM daily_supply WHERE delivery_date=? AND dish_id=? FOR UPDATE",date,dish);long next=ValueRules.nextVersion(entry.getValue());
            jdbc.update("UPDATE daily_menus SET revision=?,updated_by=?,updated_at=? WHERE delivery_date=?",next,actor,java.sql.Timestamp.from(clock.now()),date);outbox.menuChanged(date,next);
        }
        cache.invalidateAfterCommit(Set.of(dish));
    }
}
