package com.webox.catalog;
import java.time.LocalDate;
import java.util.*;
import com.webox.shared.DomainTypes.Category;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class MenuQueryRepository {
    private final JdbcTemplate jdbc;
    private static final String MEMBERS=" FROM daily_supply s JOIN dishes d ON d.id=s.dish_id WHERE s.delivery_date=? AND s.scheduled=TRUE AND d.is_listed=TRUE";
    public MenuQueryRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Member(String dishId,long dishVersion,int remaining,long inventoryVersion) {}
    public record Results(List<Member> members,long total) {}
    public Results page(LocalDate date,List<Category> categories,String search,int page,int size) {
        return page(date,categories,search,page,size,com.webox.auth.PreferencesModels.Preferences.empty(),false);
    }
    public Results page(LocalDate date,List<Category> categories,String search,int page,int size,com.webox.auth.PreferencesModels.Preferences preferences,boolean recommended) {
        var sql=new StringBuilder(MEMBERS);var params=new ArrayList<Object>();params.add(date);
        if(!categories.isEmpty()) { sql.append(" AND d.category IN (").append(String.join(",",Collections.nCopies(categories.size(),"?"))).append(")");categories.forEach(c->params.add(c.value())); }
        if(!search.isEmpty()) {
            String escaped="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            sql.append(" AND (d.name LIKE ? ESCAPE '!' OR d.description LIKE ? ESCAPE '!')");params.add(escaped);params.add(escaped);
        }
        long total=jdbc.queryForObject("SELECT COUNT(*)"+sql,Long.class,params.toArray());
        var order=new StringBuilder(" ORDER BY ");
        if(recommended && !preferences.cuisines().isEmpty()) {order.append("(d.category IN (").append(String.join(",",Collections.nCopies(preferences.cuisines().size(),"?"))).append(")) DESC,");preferences.cuisines().forEach(c->params.add(c.value()));}
        if(recommended && preferences.spiceLevel()!=null) {order.append("(d.spice=?) DESC,");params.add(preferences.spiceLevel().name());}
        order.append("d.name,d.id LIMIT ? OFFSET ?");params.add(size);params.add((long)page*size);
        var members=jdbc.query("SELECT d.id,d.version,s.total_quantity-s.allocated_quantity,s.version"+sql+order,(rs,n)->new Member(rs.getString(1),rs.getLong(2),rs.getInt(3),rs.getLong(4)),params.toArray());
        return new Results(members,total);
    }
    public Optional<Member> member(LocalDate date,String dishId) {
        return jdbc.query("SELECT d.id,d.version,s.total_quantity-s.allocated_quantity,s.version"+MEMBERS+" AND d.id=?",(rs,n)->new Member(rs.getString(1),rs.getLong(2),rs.getInt(3),rs.getLong(4)),date,dishId).stream().findFirst();
    }
}
