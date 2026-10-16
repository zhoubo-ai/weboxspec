package com.webox.inventory;
import java.time.LocalDate;
import com.webox.shared.BusinessClock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class InventoryQuery {
    private final JdbcTemplate jdbc;private final SupplyRepository supply;private final BusinessClock clock;
    public InventoryQuery(JdbcTemplate jdbc,SupplyRepository supply,BusinessClock clock){this.jdbc=jdbc;this.supply=supply;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public InventoryModels.Snapshot snapshot(LocalDate date) {
        long revision=supply.menuRevision(date);
        var rows=jdbc.query("SELECT s.dish_id,s.total_quantity-s.allocated_quantity,s.version FROM daily_supply s JOIN dishes d ON d.id=s.dish_id WHERE s.delivery_date=? AND s.scheduled=TRUE AND d.is_listed=TRUE ORDER BY s.dish_id",(rs,n)->new InventoryModels.Entry(rs.getString(1),rs.getInt(2),rs.getLong(3)),date);
        return new InventoryModels.Snapshot(date,revision,rows,clock.now());
    }
}
