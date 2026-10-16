package com.webox.catalog;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import com.webox.auth.UserIdentity;
import com.webox.inventory.OutboxWriter;
import com.webox.shared.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.webox.catalog.AdminCatalogModels.*;
@Service
public class AdminDailyMenuService {
    private final JdbcTemplate jdbc;private final OutboxWriter outbox;private final BusinessClock clock;
    public AdminDailyMenuService(JdbcTemplate jdbc,OutboxWriter outbox,BusinessClock clock){this.jdbc=jdbc;this.outbox=outbox;this.clock=clock;}
    private Optional<Long> revision(LocalDate date,boolean lock) {return jdbc.query("SELECT revision FROM daily_menus WHERE delivery_date=?"+(lock?" FOR UPDATE":""),(rs,n)->rs.getLong(1),date).stream().findFirst();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DailyMenu get(LocalDate date) {
        long revision=revision(date,false).orElse(0L);var rows=jdbc.query("SELECT s.dish_id,d.name,s.scheduled,s.total_quantity,s.allocated_quantity,s.version FROM daily_supply s JOIN dishes d ON d.id=s.dish_id WHERE s.delivery_date=? ORDER BY d.name,d.id",(rs,n)->new Supply(rs.getString(1),rs.getString(2),rs.getBoolean(3),rs.getInt(4),rs.getInt(5),rs.getInt(4)-rs.getInt(5),rs.getLong(6)),date);return new DailyMenu(date,revision,rows);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public DailyMenu replace(UserIdentity actor,LocalDate date,DailyMenuWrite body) {
        AdminDishService.admin(actor);if(body==null || body.expectedRevision()==null || body.items()==null)throw new IllegalArgumentException("Provide the menu revision and supply items.");ValueRules.safeInteger(body.expectedRevision());var requested=new TreeMap<String,Integer>();
        for(var item:body.items()){if(item==null || item.totalQuantity()==null || item.totalQuantity()<0)throw new IllegalArgumentException("Supply must be a non-negative integer.");String id=ValueRules.uuid(item.dishId());if(requested.putIfAbsent(id,item.totalQuantity())!=null)throw new IllegalArgumentException("Each dish may appear only once.");}
        AdminDishService.version(revision(date,false).orElse(0L),body.expectedRevision());
        var ids=new TreeSet<>(jdbc.queryForList("SELECT dish_id FROM daily_supply WHERE delivery_date=?",String.class,date));ids.addAll(requested.keySet());
        for(String id:ids) if(jdbc.queryForList("SELECT id FROM dishes WHERE id=? FOR UPDATE",String.class,id).isEmpty())throw ApiException.validation("items.dishId","Choose an existing dish.");
        var current=revision(date,true);AdminDishService.version(current.orElse(0L),body.expectedRevision());Timestamp now=Timestamp.from(clock.now());
        if(current.isEmpty()) {try{jdbc.update("INSERT INTO daily_menus VALUES (?,?,?,?)",date,0,actor.userId(),now);}catch(DuplicateKeyException e){throw new ApiException(409,"VERSION_CONFLICT","This menu changed. Reload and review before saving.");}}
        long next=ValueRules.nextVersion(body.expectedRevision());var events=new ArrayList<Supply>();
        for(String id:ids) {
            var rows=jdbc.query("SELECT scheduled,total_quantity,allocated_quantity,version FROM daily_supply WHERE delivery_date=? AND dish_id=? FOR UPDATE",(rs,n)->new Supply(id,"",rs.getBoolean(1),rs.getInt(2),rs.getInt(3),rs.getInt(2)-rs.getInt(3),rs.getLong(4)),date,id);boolean scheduled=requested.containsKey(id);
            if(rows.isEmpty()) {int total=requested.get(id);jdbc.update("INSERT INTO daily_supply VALUES (?,?,?,?,?,?,?)",date,id,true,total,0,1,now);events.add(new Supply(id,"",true,total,0,total,1));continue;}
            var old=rows.get(0);int total=requested.getOrDefault(id,old.totalQuantity());if(total<old.allocatedQuantity())throw new ApiException(409,"SUPPLY_BELOW_ALLOCATED","Total supply cannot be lower than the portions already allocated.");
            if(scheduled!=old.scheduled() || total!=old.totalQuantity()) {long version=ValueRules.nextVersion(old.version());jdbc.update("UPDATE daily_supply SET scheduled=?,total_quantity=?,version=?,updated_at=? WHERE delivery_date=? AND dish_id=?",scheduled,total,version,now,date,id);events.add(new Supply(id,"",scheduled,total,old.allocatedQuantity(),total-old.allocatedQuantity(),version));}
        }
        jdbc.update("UPDATE daily_menus SET revision=?,updated_by=?,updated_at=? WHERE delivery_date=?",next,actor.userId(),now,date);
        for(var event:events)outbox.inventoryUpdated(date,UUID.fromString(event.dishId()),event.remaining(),event.version(),next);outbox.menuChanged(date,next);return get(date);
    }
}
