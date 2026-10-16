package com.webox.ordering;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.*;
import static com.webox.ordering.OrderingModels.*;
@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;private final OrderItemRepository items;
    public OrderRepository(JdbcTemplate jdbc,OrderItemRepository items) { this.jdbc=jdbc;this.items=items; }
    public record Row(String id,String userId,String quoteId,String key,String hash,LocalDate date,Meal meal,String address,OrderStatus status,int quantity,long total,long version,Instant createdAt,Instant updatedAt) { @Override public String toString() { return "OrderRow[redacted]"; } }
    private static final RowMapper<Row> ROW=(rs,n)->new Row(rs.getString("id"),rs.getString("user_id"),rs.getString("quote_id"),rs.getString("idempotency_key"),rs.getString("request_hash"),rs.getDate("delivery_date").toLocalDate(),Meal.valueOf(rs.getString("meal")),rs.getString("delivery_address_snapshot"),OrderStatus.valueOf(rs.getString("status")),rs.getInt("total_quantity"),rs.getLong("total_cents"),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    public Optional<Row> byKey(String user,String key) { return jdbc.query("SELECT * FROM orders WHERE user_id=? AND idempotency_key=?",ROW,user,key).stream().findFirst(); }
    public Optional<Row> byQuote(String user,String quote) { return jdbc.query("SELECT * FROM orders WHERE user_id=? AND quote_id=?",ROW,user,quote).stream().findFirst(); }
    public Optional<Row> find(String id) { return jdbc.query("SELECT * FROM orders WHERE id=?",ROW,id).stream().findFirst(); }
    public Row lock(String id,String user) { return jdbc.query("SELECT * FROM orders WHERE id=? AND user_id=? FOR UPDATE",ROW,id,user).stream().findFirst().orElseThrow(OrderRepository::missing); }
    public Optional<String> active(String user,LocalDate date,Meal meal) { return jdbc.query("SELECT id FROM orders WHERE user_id=? AND delivery_date=? AND meal=? AND active_slot=1",(rs,n)->rs.getString(1),user,date,meal.name()).stream().findFirst(); }
    public Order view(Row row) { return new Order(row.id(),"WB-"+row.id().toUpperCase(Locale.ROOT),row.date(),row.meal(),row.address(),row.status(),items.list(row.id()),row.quantity(),row.total(),row.version(),row.createdAt(),row.updatedAt()); }
    public Order owned(String id,String user) { var row=find(id).filter(value->value.userId().equals(user)).orElseThrow(OrderRepository::missing);return view(row); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public OrderPage page(String user,OrderStatus status,int page,int size) {
        String filter=" WHERE user_id=?"+(status==null?"":" AND status=?");var params=new ArrayList<Object>();params.add(user);if(status!=null)params.add(status.name());
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM orders"+filter,Long.class,params.toArray());params.add(size);params.add((long)page*size);
        var rows=jdbc.query("SELECT * FROM orders"+filter+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",ROW,params.toArray());return new OrderPage(rows.stream().map(this::view).toList(),page,size,count);
    }
    public record OperationalRows(List<Row> rows,long total) {}
    public OperationalRows operationalPage(LocalDate date,Meal meal,OrderStatus status,int page,int size) {
        var filter=new StringBuilder(" WHERE 1=1");var params=new ArrayList<Object>();
        if(date!=null){filter.append(" AND delivery_date=?");params.add(date);}if(meal!=null){filter.append(" AND meal=?");params.add(meal.name());}if(status!=null){filter.append(" AND status=?");params.add(status.name());}
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM orders"+filter,Long.class,params.toArray());params.add(size);params.add((long)page*size);
        return new OperationalRows(jdbc.query("SELECT * FROM orders"+filter+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",ROW,params.toArray()),count);
    }
    public Row insert(String user,IdempotencyRequest request,QuoteRepository.Row quote,String address,Instant now) {
        String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO orders(id,user_id,quote_id,idempotency_key,request_hash,delivery_date,meal,delivery_address_snapshot,status,total_quantity,total_cents,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,user,quote.id(),request.key(),request.hash(),quote.date(),quote.meal().name(),address,"Pending",quote.quantity(),quote.total(),1,Timestamp.from(now),Timestamp.from(now));
        return new Row(id,user,quote.id(),request.key(),request.hash(),quote.date(),quote.meal(),address,OrderStatus.Pending,quote.quantity(),quote.total(),1,now,now);
    }
    public void transition(Row row,OrderStatus target,Instant now) {
        String timestamp=switch(target) {case Confirmed->"confirmed_at";case Completed->"completed_at";case Cancelled->"cancelled_at";default->throw new IllegalArgumentException("Invalid target status.");};
        if(jdbc.update("UPDATE orders SET status=?,version=?,updated_at=?,"+timestamp+"=? WHERE id=? AND version=?",target.name(),ValueRules.nextVersion(row.version()),Timestamp.from(now),Timestamp.from(now),row.id(),row.version())!=1) throw new IllegalStateException("Locked order version changed.");
    }
    public static ApiException missing() { return new ApiException(404,"NOT_FOUND","This order was not found."); }
}
