package com.webox.ordering;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;
import static com.webox.ordering.OrderingModels.*;
@Repository
public class OrderItemRepository {
    private final JdbcTemplate jdbc;private final JsonMapper json;
    public OrderItemRepository(JdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc;this.json=json; }
    public List<PricedLine> list(String order) { return jdbc.query("SELECT * FROM order_items WHERE order_id=? ORDER BY dish_id,configuration_key",(rs,n)->SnapshotRows.read(rs,json,"dish_version_snapshot"),order); }
    public void insert(String order,List<PricedLine> items) {
        for(var item:items) jdbc.update("INSERT INTO order_items(id,order_id,dish_id,dish_name_snapshot,dish_version_snapshot,configuration_key,options_snapshot,effective_allergens_snapshot,base_price_cents,unit_price_cents,quantity,subtotal_cents) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),order,item.dishId(),item.dishName(),item.dishVersion(),item.configurationKey(),json.writeValueAsString(item.selectedOptions()),json.writeValueAsString(item.effectiveAllergens()),item.basePriceCents(),item.unitPriceCents(),item.quantity(),item.subtotalCents());
    }
}
