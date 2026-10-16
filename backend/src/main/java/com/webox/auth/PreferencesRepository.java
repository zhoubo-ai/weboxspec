package com.webox.auth;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.webox.shared.DomainTypes.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static com.webox.auth.PreferencesModels.*;
@Repository
public class PreferencesRepository {
    private final JdbcTemplate jdbc;
    public PreferencesRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<Preferences> find(String user,boolean lock) {
        if(lock) requireWrite();
        var roots=jdbc.query("SELECT spice,flavor,budget_min_cents,budget_max_cents,version FROM dietary_preferences WHERE user_id=?"+(lock?" FOR UPDATE":""),(rs,n)->new Preferences(List.of(),List.of(),rs.getString(1)==null?null:SpiceLevel.valueOf(rs.getString(1)),rs.getString(2)==null?null:Flavor.valueOf(rs.getString(2)),rs.getObject(3,Long.class),rs.getObject(4,Long.class),rs.getLong(5)),user);
        if(roots.isEmpty()) return Optional.empty();var root=roots.get(0);
        var allergens=jdbc.query("SELECT allergen FROM preference_allergens WHERE user_id=? ORDER BY allergen",(rs,n)->Allergen.valueOf(rs.getString(1)),user);
        var cuisines=jdbc.query("SELECT category FROM preference_cuisines WHERE user_id=? ORDER BY category",(rs,n)->Category.parse(rs.getString(1)),user);
        return Optional.of(new Preferences(allergens,cuisines,root.spiceLevel(),root.flavor(),root.budgetMinCents(),root.budgetMaxCents(),root.version()));
    }
    public Preferences get(String user) { return find(user,false).orElseGet(Preferences::empty); }
    public void replace(String user,Preferences value,boolean exists,Instant now) {
        requireWrite();String spice=value.spiceLevel()==null?null:value.spiceLevel().name(),flavor=value.flavor()==null?null:value.flavor().name();
        if(exists) jdbc.update("UPDATE dietary_preferences SET spice=?,flavor=?,budget_min_cents=?,budget_max_cents=?,version=?,updated_at=? WHERE user_id=?",spice,flavor,value.budgetMinCents(),value.budgetMaxCents(),value.version(),Timestamp.from(now),user);
        else jdbc.update("INSERT INTO dietary_preferences VALUES (?,?,?,?,?,?,?)",user,spice,flavor,value.budgetMinCents(),value.budgetMaxCents(),value.version(),Timestamp.from(now));
        jdbc.update("DELETE FROM preference_allergens WHERE user_id=?",user);jdbc.update("DELETE FROM preference_cuisines WHERE user_id=?",user);
        for(var allergen:value.allergens()) jdbc.update("INSERT INTO preference_allergens VALUES (?,?)",user,allergen.name());
        for(var category:value.cuisines()) jdbc.update("INSERT INTO preference_cuisines VALUES (?,?)",user,category.value());
    }
    private static void requireWrite() { if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new IllegalStateException("Preferences writes require a transaction."); }
}
