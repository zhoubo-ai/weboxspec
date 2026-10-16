package com.webox.recommendation;
import com.webox.auth.PreferencesModels.Preferences;
import com.webox.auth.PreferencesRepository;
import com.webox.catalog.MenuModels.MenuDish;
import com.webox.catalog.MenuService;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CandidateService {
    private final com.webox.catalog.MenuQueryRepository membership;private final MenuService menus;private final PreferencesRepository preferences;private final JdbcTemplate jdbc;private final BusinessClock clock;
    public CandidateService(com.webox.catalog.MenuQueryRepository membership,MenuService menus,PreferencesRepository preferences,JdbcTemplate jdbc,BusinessClock clock){this.membership=membership;this.menus=menus;this.preferences=preferences;this.jdbc=jdbc;this.clock=clock;}
    public record Candidate(MenuDish dish,long minimumPriceCents,boolean recent){}
    public record Prepared(Preferences preferences,Set<String> history,List<Candidate> candidates){public Prepared{history=Set.copyOf(history);candidates=List.copyOf(candidates);}}
    public Prepared prepare(String user,LocalDate date,IntentService.Intent intent) {
        var personal=preferences.get(user);var recent=history(user,clock.today().minusDays(6),clock.today());var excluded=intent.hardRepeat()?history(user,intent.repeatFrom(),intent.repeatThrough()):Set.<String>of();var candidates=new ArrayList<Candidate>();
        for(int page=0;;page++) {
            var result=menus.page(user,date,List.of(),"",page,100,false);
            for(var dish:result.items())if(eligible(dish,personal,intent,excluded))candidates.add(new Candidate(dish,minimumPrice(dish),recent.contains(dish.dishId())));
            if((long)(page+1)*100>=result.totalElements())break;
        }
        candidates.sort(Comparator.comparing(Candidate::recent).thenComparing(Comparator.comparingInt((Candidate candidate)->score(candidate,personal,intent)).reversed()).thenComparing(c->c.dish().name()).thenComparing(c->c.dish().dishId()));
        return new Prepared(personal,recent,candidates.stream().limit(30).toList());
    }
    public Optional<MenuDish> recheck(String user,LocalDate date,IntentService.Intent intent,String dishId) {
        var personal=preferences.get(user);var excluded=intent.hardRepeat()?history(user,intent.repeatFrom(),intent.repeatThrough()):Set.<String>of();
        if(membership.member(date,dishId).isEmpty())return Optional.empty();
        var dish=menus.dish(user,date,dishId);return eligible(dish,personal,intent,excluded)?Optional.of(dish):Optional.empty();
    }
    public Set<String> history(String user,LocalDate from,LocalDate through) {
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT i.dish_id FROM orders o JOIN order_items i ON i.order_id=o.id WHERE o.user_id=? AND o.delivery_date>=? AND o.delivery_date<=? AND o.status<>'Cancelled'",String.class,user,from,through));
    }
    public static long minimumPrice(MenuDish dish) {
        long price=dish.basePriceCents();for(var group:dish.optionGroups())if(group.required()){long extra=group.options().stream().mapToLong(o->o.extraCents()).min().orElseThrow(()->new IllegalStateException("A required group has no options."));price=ValueRules.add(price,extra);}return price;
    }
    private boolean eligible(MenuDish dish,Preferences personal,IntentService.Intent intent,Set<String> excluded) {
        if(dish.remaining()<=0 || excluded.contains(dish.dishId()))return false;
        var flagged=new HashSet<>(personal.allergens());flagged.addAll(intent.excludedAllergens());var labels=new HashSet<>(dish.allergens());dish.optionGroups().forEach(group->group.options().forEach(option->labels.addAll(option.allergens())));if(!Collections.disjoint(flagged,labels))return false;
        if(!intent.cuisines().isEmpty() && !intent.cuisines().contains(dish.category()))return false;
        if(intent.spiceLevel()!=null && !(intent.spiceLevel()==SpiceLevel.Mild?(dish.spiceLevel()==SpiceLevel.None || dish.spiceLevel()==SpiceLevel.Mild):intent.spiceLevel()==SpiceLevel.Hot?(dish.spiceLevel()==SpiceLevel.Medium || dish.spiceLevel()==SpiceLevel.Hot):dish.spiceLevel()==intent.spiceLevel()))return false;
        if(intent.budgetMaxCents()!=null && minimumPrice(dish)>intent.budgetMaxCents())return false;
        if(intent.dishQuery()!=null && !Arrays.stream(intent.dishQuery().toLowerCase(Locale.ROOT).split("\\s+")).allMatch(word->dish.name().toLowerCase(Locale.ROOT).contains(word)))return false;
        return intent.protein()==null || (dish.protein()+" "+dish.name()+" "+dish.description()).toLowerCase(Locale.ROOT).contains(intent.protein().toLowerCase(Locale.ROOT));
    }
    private int score(Candidate candidate,Preferences preferences,IntentService.Intent intent) {
        var dish=candidate.dish();int score=preferences.cuisines().contains(dish.category())?4:0;
        if(preferences.spiceLevel()==dish.spiceLevel())score+=2;
        if(preferences.budgetMaxCents()!=null && candidate.minimumPriceCents()>=preferences.budgetMinCents() && candidate.minimumPriceCents()<=preferences.budgetMaxCents())score++;
        return score;
    }
}
