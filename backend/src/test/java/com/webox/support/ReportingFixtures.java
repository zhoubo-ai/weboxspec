package com.webox.support;
import com.webox.shared.DomainTypes.Role;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
/** Test-only immutable order snapshots and coherent supply rows. Expected totals are handwritten separately. */
public final class ReportingFixtures {
    private static final AtomicInteger SERIAL=new AtomicInteger();private final JdbcTemplate jdbc;private final CatalogFixtures catalog;private final FixtureFactory identities;
    public ReportingFixtures(JdbcTemplate jdbc,CatalogFixtures catalog,FixtureFactory identities){this.jdbc=jdbc;this.catalog=catalog;this.identities=identities;}
    public record Fixture(LocalDate date,LocalDate today,Map<String,String> dishes,Map<String,String> orders){}
    public Fixture create() {
        var base=catalog.create();LocalDate date=LocalDate.of(2051,3,8).plusYears(10L*SERIAL.getAndIncrement()),today=date.plusDays(7);var pair=new ArrayList<>(List.of(base.burger(),base.pasta()));Collections.sort(pair);var dishes=Map.of("A",pair.get(0),"B",pair.get(1),"C",base.chinese(),"J",base.japanese());
        var params=new HashMap<String,Object>();params.putAll(dishes);params.put("D",date);params.put("option",UUID.randomUUID().toString());params.put("groupId",UUID.randomUUID().toString());var orders=new LinkedHashMap<String,String>();
        for(int i=1;i<=18;i++){String id=UUID.randomUUID().toString();orders.put("o"+i,id);params.put("o"+i,id);}for(int i=1;i<=3;i++)params.put("u"+i,identities.identity(Role.Employee).user().userId());
        for(LocalDate day=date.minusDays(6);!day.isAfter(today);day=day.plusDays(1)){
            jdbc.update("INSERT INTO daily_menus VALUES (?,?,?,?)",day,1,base.adminId(),java.sql.Timestamp.from(MySqlTestSupport.INITIAL_TIME));
            jdbc.update("INSERT INTO daily_supply SELECT ?,dish_id,scheduled,20,0,0,updated_at FROM daily_supply WHERE delivery_date=?",day,base.date());
        }
        try(var input=ReportingFixtures.class.getResourceAsStream("/reporting/seven-day-orders.sql")){if(input==null)throw new IllegalStateException("Missing reporting fixture.");String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8);var named=new NamedParameterJdbcTemplate(jdbc);for(String statement:sql.split(";"))if(!statement.isBlank())named.update(statement,params);}catch(IOException failure){throw new IllegalStateException(failure);}
        jdbc.update("UPDATE daily_supply s JOIN (SELECT o.delivery_date,i.dish_id,SUM(i.quantity) allocated FROM orders o JOIN order_items i ON i.order_id=o.id WHERE o.delivery_date BETWEEN ? AND ? AND o.status<>'Cancelled' GROUP BY o.delivery_date,i.dish_id) used ON used.delivery_date=s.delivery_date AND used.dish_id=s.dish_id SET s.allocated_quantity=used.allocated",date.minusDays(6),today);
        for(String key:List.of("A","B","C"))jdbc.update("UPDATE dishes SET name=?,version=version+1 WHERE id=?","Current menu "+key,dishes.get(key));
        for(var day:List.of(date,today)) {
            var remaining=day.equals(date)?Map.of("A",3,"B",0,"C",4,"J",5):Map.of("A",0,"B",2,"C",1,"J",20);
            remaining.forEach((key,quantity)->jdbc.update("UPDATE daily_supply SET total_quantity=allocated_quantity+? WHERE delivery_date=? AND dish_id=?",quantity,day,dishes.get(key)));
            jdbc.update("UPDATE daily_supply SET total_quantity=8 WHERE delivery_date=? AND dish_id=?",day,base.salad());jdbc.update("UPDATE daily_supply SET total_quantity=1 WHERE delivery_date=? AND dish_id=?",day,base.hidden());jdbc.update("UPDATE daily_supply SET total_quantity=0 WHERE delivery_date=? AND dish_id=?",day,base.unscheduled());
        }
        return new Fixture(date,today,Map.copyOf(dishes),Map.copyOf(orders));
    }
}
