package com.webox.ordering;
import java.util.*;
import com.webox.auth.PreferencesRepository;
import com.webox.shared.ValueRules;
import org.springframework.stereotype.Component;
import static com.webox.ordering.OrderingModels.*;
@Component
public class CheckoutWarnings {
    private final PreferencesRepository preferences;
    public CheckoutWarnings(PreferencesRepository preferences) {this.preferences=preferences;}
    public List<Map<String,Object>> forItems(String user,List<PricedLine> items) {
        var personal=preferences.get(user);var warnings=new ArrayList<Map<String,Object>>();long total=0;
        for(int index=0;index<items.size();index++) {
            var item=items.get(index);total=ValueRules.add(total,item.subtotalCents());var matches=item.effectiveAllergens().stream().filter(personal.allergens()::contains).map(Enum::name).sorted().toList();
            if(!matches.isEmpty()) warnings.add(Map.of("code","FLAGGED_ALLERGEN","message","This selection contains your flagged allergens: "+String.join(", ",matches)+". You may continue if you choose.","dishId",item.dishId(),"itemIndex",index,"allergens",matches));
        }
        if(personal.budgetMaxCents()!=null && total>personal.budgetMaxCents()) warnings.add(Map.of("code","BUDGET_EXCEEDED","message","This order exceeds your maximum meal budget. You can still place the order."));
        return List.copyOf(warnings);
    }
}
