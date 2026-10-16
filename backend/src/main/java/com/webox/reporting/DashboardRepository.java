package com.webox.reporting;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.webox.shared.ValueRules;
import static com.webox.reporting.DashboardModels.*;
@Repository
public class DashboardRepository {
    private final JdbcTemplate jdbc;
    public DashboardRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    // MySQL SUM(BIGINT) is DECIMAL. Check before serializing to JavaScript-safe integer cents/counts.
    private static long total(ResultSet row,String field)throws SQLException {
        try {var value=row.getBigDecimal(field);long exact=value==null?0:value.longValueExact();if(exact<0 || exact>ValueRules.MAX_SAFE_INTEGER)throw new ArithmeticException();return exact;}
        catch(ArithmeticException failure){throw new IllegalStateException("Dashboard aggregate exceeds the supported range.");}
    }
    public Summary summary(LocalDate date) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) total_orders, COALESCE(SUM(CASE WHEN status='Completed' THEN total_cents ELSE 0 END),0) revenue,
              COALESCE(SUM(status='Completed'),0) completed, COALESCE(SUM(status IN ('Pending','Confirmed')),0) pending,
              COALESCE(SUM(status='Cancelled'),0) cancelled, COALESCE(SUM(meal='Lunch'),0) lunch, COALESCE(SUM(meal='Dinner'),0) dinner
            FROM orders WHERE delivery_date=?
            """,(row,n)->new Summary(total(row,"total_orders"),total(row,"revenue"),total(row,"completed"),total(row,"pending"),total(row,"cancelled"),total(row,"lunch"),total(row,"dinner")),date);
    }
    public List<TopDish> topDishes(LocalDate date) {
        return jdbc.query("""
            WITH counted AS (
              SELECT i.dish_id,i.dish_name_snapshot,i.quantity,
                ROW_NUMBER() OVER(PARTITION BY i.dish_id ORDER BY o.created_at DESC,o.id DESC,i.id DESC) latest
              FROM orders o JOIN order_items i ON i.order_id=o.id WHERE o.delivery_date=? AND o.status='Completed'
            )
            SELECT dish_id,MAX(CASE WHEN latest=1 THEN dish_name_snapshot END) name,SUM(quantity) quantity
            FROM counted GROUP BY dish_id ORDER BY quantity DESC,dish_id ASC LIMIT 10
            """,(row,n)->new TopDish(row.getString("dish_id"),row.getString("name"),total(row,"quantity")),date);
    }
    public List<Day> trend(LocalDate date) {
        return jdbc.query("""
            SELECT delivery_date,COUNT(*) orders,COALESCE(SUM(CASE WHEN status='Completed' THEN total_cents ELSE 0 END),0) revenue
            FROM orders WHERE delivery_date BETWEEN ? AND ? GROUP BY delivery_date ORDER BY delivery_date
            """,(row,n)->new Day(row.getObject("delivery_date",LocalDate.class),total(row,"orders"),total(row,"revenue")),date.minusDays(6),date);
    }
    public List<InventoryAlert> inventory(LocalDate date) {
        return jdbc.query("""
            SELECT d.id,d.name,s.total_quantity-s.allocated_quantity remaining FROM daily_supply s JOIN dishes d ON d.id=s.dish_id
            WHERE s.delivery_date=? AND s.scheduled=TRUE AND d.is_listed=TRUE AND s.total_quantity-s.allocated_quantity BETWEEN 0 AND 3
            ORDER BY remaining ASC,d.id ASC
            """,(row,n)->new InventoryAlert(row.getString("id"),row.getString("name"),total(row,"remaining"),row.getLong("remaining")==0?"SoldOut":"LowStock"),date);
    }
}
