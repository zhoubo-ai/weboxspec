package com.webox.inventory;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.webox.shared.*;
import com.webox.ordering.QuoteService;
import com.webox.ordering.OrderingModels.PricedLine;
@Service
public class InventoryMutationService {
    private final JdbcTemplate jdbc;private final SupplyRepository supply;private final OutboxWriter outbox;private final BusinessClock clock;
    public InventoryMutationService(JdbcTemplate jdbc,SupplyRepository supply,OutboxWriter outbox,BusinessClock clock) { this.jdbc=jdbc;this.supply=supply;this.outbox=outbox;this.clock=clock; }
    public record Dish(String id,long version,boolean listed) {}
    public record Locked(LocalDate date,SortedMap<String,Integer> demand,Map<String,Dish> dishes,Map<String,SupplyRepository.Supply> supplies) {}
    public Locked lock(LocalDate date,List<PricedLine> items) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new IllegalStateException("Inventory requires a business write transaction.");
        var demand=new TreeMap<String,Integer>();for(var item:items) demand.merge(item.dishId(),item.quantity(),Math::addExact);
        var roots=new HashMap<String,Dish>();var rows=new HashMap<String,SupplyRepository.Supply>();
        // Complete every root lock before taking any supply lock; never rely on IN-list order.
        for(String id:demand.keySet()) roots.put(id,jdbc.query("SELECT id,version,is_listed FROM dishes WHERE id=? FOR UPDATE",(rs,n)->new Dish(rs.getString(1),rs.getLong(2),rs.getBoolean(3)),id).stream().findFirst().orElseThrow(()->QuoteService.itemError("DISH_UNAVAILABLE",id,"This dish is no longer available.",Map.of())));
        for(String id:demand.keySet()) rows.put(id,jdbc.query("SELECT total_quantity,allocated_quantity,version,scheduled FROM daily_supply WHERE delivery_date=? AND dish_id=? FOR UPDATE",(rs,n)->new SupplyRepository.Supply(date,id,rs.getInt(1),rs.getInt(2),rs.getLong(3),rs.getBoolean(4)),date,id).stream().findFirst().orElseThrow(()->QuoteService.itemError("DISH_UNAVAILABLE",id,"This dish is not scheduled for the selected date.",Map.of())));
        return new Locked(date,Collections.unmodifiableSortedMap(demand),Map.copyOf(roots),Map.copyOf(rows));
    }
    public void validateForPurchase(Locked locked,List<PricedLine> items) {
        for(var item:items) {
            var dish=locked.dishes().get(item.dishId());var row=locked.supplies().get(item.dishId());
            if(!dish.listed() || !row.scheduled()) throw QuoteService.itemError("DISH_UNAVAILABLE",dish.id(),"This dish is no longer available on the selected date.",Map.of());
            if(dish.version()!=item.dishVersion()) throw QuoteService.itemError("DISH_CHANGED",dish.id(),"This dish has changed. Confirm a new quote before ordering.",Map.of("currentDishVersion",dish.version()));
        }
    }
    public void allocate(Locked locked) { mutate(locked,false); }
    public void restore(Locked locked) { mutate(locked,true); }
    private void mutate(Locked locked,boolean restore) {
        for(var entry:locked.demand().entrySet()) {
            var row=locked.supplies().get(entry.getKey());int quantity=entry.getValue();
            if(!restore && row.remaining()<quantity) throw QuoteService.itemError("INSUFFICIENT_STOCK",row.dishId(),"This dish no longer has enough stock. Please adjust your cart.",Map.of("availableQuantity",row.remaining()));
            if(restore && row.allocated()<quantity) throw new IllegalStateException("Cannot restore more than the allocated stock.");
            int allocated=restore?row.allocated()-quantity:Math.addExact(row.allocated(),quantity);long version=ValueRules.nextVersion(row.version());
            int count=jdbc.update("UPDATE daily_supply SET allocated_quantity=?,version=?,updated_at=? WHERE delivery_date=? AND dish_id=? AND version=? AND total_quantity>=?",allocated,version,java.sql.Timestamp.from(clock.now()),row.date(),row.dishId(),row.version(),allocated);
            if(count!=1) throw new IllegalStateException("Locked inventory changed unexpectedly.");
            outbox.inventoryUpdated(row.date(),UUID.fromString(row.dishId()),row.total()-allocated,version,supply.menuRevision(row.date()));
        }
    }
}
