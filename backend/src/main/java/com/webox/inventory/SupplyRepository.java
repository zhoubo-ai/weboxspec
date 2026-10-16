package com.webox.inventory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class SupplyRepository {
    private final JdbcTemplate jdbc;
    public SupplyRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long menuRevision(LocalDate date) { return jdbc.query("SELECT revision FROM daily_menus WHERE delivery_date=?",(rs,n)->rs.getLong(1),date).stream().findFirst().orElse(0L); }
    public Optional<Supply> find(LocalDate date,String dishId) {
        return jdbc.query("SELECT total_quantity,allocated_quantity,version,scheduled FROM daily_supply WHERE delivery_date=? AND dish_id=?",(rs,n)->new Supply(date,dishId,rs.getInt(1),rs.getInt(2),rs.getLong(3),rs.getBoolean(4)),date,dishId).stream().findFirst();
    }
    public record Supply(LocalDate date,String dishId,int total,int allocated,long version,boolean scheduled) { public int remaining() { return total-allocated; } }
}
