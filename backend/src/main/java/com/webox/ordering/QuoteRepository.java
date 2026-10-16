package com.webox.ordering;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import com.webox.shared.DomainTypes.Meal;
import static com.webox.ordering.OrderingModels.*;
@Repository
public class QuoteRepository {
    private final JdbcTemplate jdbc;private final JsonMapper json;
    public QuoteRepository(JdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc;this.json=json; }
    public record Row(String id,String userId,LocalDate date,Meal meal,int quantity,long total,Instant createdAt,Instant expiresAt,String consumedOrderId) {}
    public Optional<Row> lockOwned(String id,String user) {
        return owned(id,user,true);
    }
    public Optional<Row> findOwned(String id,String user) { return owned(id,user,false); }
    private Optional<Row> owned(String id,String user,boolean lock) { return jdbc.query("SELECT * FROM checkout_quotes WHERE id=? AND user_id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Row(rs.getString("id"),rs.getString("user_id"),rs.getDate("delivery_date").toLocalDate(),Meal.valueOf(rs.getString("meal")),rs.getInt("total_quantity"),rs.getLong("total_cents"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("expires_at").toInstant(),rs.getString("consumed_order_id")),id,user).stream().findFirst(); }
    public List<PricedLine> items(String id) { return jdbc.query("SELECT * FROM checkout_quote_items WHERE quote_id=? ORDER BY dish_id,configuration_key",(rs,n)->SnapshotRows.read(rs,json,"dish_version"),id); }
    public void insert(String user,Quote quote) {
        jdbc.update("INSERT INTO checkout_quotes(id,user_id,delivery_date,meal,total_quantity,total_cents,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?)",quote.quoteId(),user,quote.deliveryDate(),quote.meal().name(),quote.totalQuantity(),quote.totalCents(),Timestamp.from(quote.serverTime()),Timestamp.from(quote.expiresAt()));
        for(var item:quote.items()) jdbc.update("INSERT INTO checkout_quote_items(id,quote_id,dish_id,dish_version,configuration_key,dish_name_snapshot,options_snapshot,effective_allergens_snapshot,base_price_cents,unit_price_cents,quantity,subtotal_cents) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),quote.quoteId(),item.dishId(),item.dishVersion(),item.configurationKey(),item.dishName(),json.writeValueAsString(item.selectedOptions()),json.writeValueAsString(item.effectiveAllergens()),item.basePriceCents(),item.unitPriceCents(),item.quantity(),item.subtotalCents());
    }
    public void consume(String id,String order) { if(jdbc.update("UPDATE checkout_quotes SET consumed_order_id=? WHERE id=? AND consumed_order_id IS NULL",order,id)!=1) throw new IllegalStateException("Locked quote was already consumed."); }
}
