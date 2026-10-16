package com.webox.catalog;
import java.util.*;
import com.webox.shared.ApiException;
import com.webox.shared.DomainTypes.*;
import static com.webox.catalog.MenuModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class DishRepository {
    private final JdbcTemplate jdbc;
    public DishRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Details details(String id,long version) {
        var base=jdbc.query("SELECT id,name,description,media_id,base_price_cents,category,protein_source,spice,version FROM dishes WHERE id=? AND version=?",
            (rs,n)->new Details(rs.getString(1),rs.getString(2),rs.getString(3),"/media/"+rs.getString(4),rs.getLong(5),Category.parse(rs.getString(6)),rs.getString(7),List.of(),SpiceLevel.valueOf(rs.getString(8)),List.of(),rs.getLong(9)),id,version).stream().findFirst()
                .orElseThrow(()->new ApiException(404,"NOT_FOUND","This dish is not available."));
        var allergens=jdbc.query("SELECT allergen FROM dish_allergens WHERE dish_id=? ORDER BY allergen",(rs,n)->Allergen.valueOf(rs.getString(1)),id);
        Map<String,List<Allergen>> optionAllergens=new HashMap<>();
        jdbc.query("SELECT a.option_id,a.allergen FROM option_allergens a JOIN customization_options o ON o.id=a.option_id JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=? ORDER BY a.allergen",rs->{optionAllergens.computeIfAbsent(rs.getString(1),k->new ArrayList<>()).add(Allergen.valueOf(rs.getString(2)));},id);
        Map<String,List<Option>> options=new HashMap<>();
        jdbc.query("SELECT o.group_id,o.id,o.name,o.extra_price_cents FROM customization_options o JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=? ORDER BY o.display_order,o.id",rs->{String option=rs.getString(2);options.computeIfAbsent(rs.getString(1),k->new ArrayList<>()).add(new Option(option,rs.getString(3),rs.getLong(4),optionAllergens.getOrDefault(option,List.of())));},id);
        var groups=jdbc.query("SELECT id,name,required,selection_mode FROM customization_groups WHERE dish_id=? ORDER BY display_order,id",
            (rs,n)->new OptionGroup(rs.getString(1),rs.getString(2),rs.getBoolean(3),SelectionType.valueOf(rs.getString(4)),options.getOrDefault(rs.getString(1),List.of())),id);
        return new Details(base.dishId(),base.name(),base.description(),base.imageUrl(),base.basePriceCents(),base.category(),base.protein(),allergens,base.spiceLevel(),groups,base.dishVersion());
    }
}
