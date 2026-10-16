package com.webox.shared;

import com.webox.auth.UserIdentity;
import com.webox.catalog.AdminDailyMenuService;
import com.webox.catalog.AdminDishService;
import com.webox.media.MediaService;
import com.webox.shared.DomainTypes.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import static com.webox.catalog.AdminCatalogModels.*;

/** Only invoked by the explicit seed runner; never by normal application startup. */
@Service
public class MenuSeeder {
    public record SeedDish(String slug,String name,String description,long basePriceCents,Category category,
                           String protein,List<Allergen> allergens,SpiceLevel spiceLevel,String image,List<GroupCreate> optionGroups) {}
    private final JdbcTemplate jdbc;private final AdminDishService dishes;private final AdminDailyMenuService menus;
    private final MediaService media;private final BusinessClock clock;private final JsonMapper json;
    public MenuSeeder(JdbcTemplate jdbc,AdminDishService dishes,AdminDailyMenuService menus,MediaService media,BusinessClock clock,JsonMapper json) {
        this.jdbc=jdbc;this.dishes=dishes;this.menus=menus;this.media=media;this.clock=clock;this.json=json;
    }
    public static String dishId(String slug) {return UUID.nameUUIDFromBytes(("webox.seed.v1."+slug).getBytes(StandardCharsets.UTF_8)).toString();}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void initialize(UserIdentity actor) {
        AdminDishService.admin(actor);
        jdbc.queryForObject("SELECT name FROM seed_locks WHERE name='menu-v1' FOR UPDATE",String.class);
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",String.class,actor.userId());
        try {
            List<SeedDish> definitions;
            try(var input=new ClassPathResource("seed/menu.json").getInputStream()) {definitions=List.of(json.readValue(input,SeedDish[].class));}
            if(definitions.size()!=9 || definitions.stream().map(SeedDish::slug).distinct().count()!=9)throw new IllegalStateException("Invalid bundled seed manifest.");
            definitions=definitions.stream().sorted(Comparator.comparing(d->dishId(d.slug()))).toList();
            // All dish roots precede all date/supply locks, as in operational writers.
            for(var dish:definitions) {
                String id=dishId(dish.slug());
                if(!jdbc.queryForList("SELECT id FROM dishes WHERE id=? FOR UPDATE",String.class,id).isEmpty())continue;
                if(!dish.image().matches("[a-z0-9-]+\\.png"))throw new IllegalStateException("Invalid bundled image path.");
                byte[] bytes;try(var input=new ClassPathResource("seed/images/"+dish.image()).getInputStream()){bytes=input.readAllBytes();}
                var asset=media.store(actor,bytes,"image/png",dish.image(),"Bundled seed illustration; see seed/ATTRIBUTION.md");
                var created=dishes.createWithId(actor,id,new DishCreate(dish.name(),dish.description(),dish.basePriceCents(),dish.category(),dish.protein(),dish.allergens(),dish.spiceLevel(),asset.mediaId(),dish.optionGroups()));
                dishes.publish(actor,id,new Publication(true,created.dishVersion()));
            }
            LocalDate today=clock.today();
            for(LocalDate date:List.of(today,today.plusDays(1))) {
                // An existing menu, including an intentionally empty one, is an operator's decision.
                if(!jdbc.queryForList("SELECT delivery_date FROM daily_menus WHERE delivery_date=?",LocalDate.class,date).isEmpty())continue;
                menus.replace(actor,date,new DailyMenuWrite(0L,definitions.stream().map(d->new SupplyWrite(dishId(d.slug()),20)).toList()));
            }
        } catch(IOException e) {throw new IllegalStateException("The bundled seed resources could not be read.",e);}
    }
}
