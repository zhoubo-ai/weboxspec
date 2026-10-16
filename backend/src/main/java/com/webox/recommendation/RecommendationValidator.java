package com.webox.recommendation;
import java.util.*;
import java.util.regex.Pattern;
import com.webox.shared.ValueRules;
import com.webox.catalog.MenuModels.MenuDish;
import com.webox.auth.PreferencesRepository;
import com.webox.shared.BusinessClock;
import com.webox.shared.DomainTypes.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class RecommendationValidator {
    private final CandidateService candidates;private final PreferencesRepository preferences;private final BusinessClock clock;
    public RecommendationValidator(CandidateService candidates,PreferencesRepository preferences,BusinessClock clock){this.candidates=candidates;this.preferences=preferences;this.clock=clock;}
    /** A new snapshot for each card; no transaction is held during an upstream call. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=Propagation.REQUIRES_NEW)
    public Optional<MenuDish> current(String user,LocalDate date,IntentService.Intent intent,CandidateService.Prepared offered,RecommendationModels.Generated item) {
        var original=offered.candidates().stream().filter(candidate->candidate.dish().dishId().equals(item.dishId())).findFirst().orElseThrow(AiFailure::invalid);
        var personal=preferences.get(user);
        var current=candidates.recheck(user,date,intent,item.dishId());
        if(current.isEmpty() || current.get().dishVersion()!=original.dish().dishVersion() || personal.version()!=offered.preferences().version())return Optional.empty();
        boolean recent=candidates.history(user,clock.today().minusDays(6),clock.today()).contains(item.dishId());
        if(recent!=original.recent())return Optional.empty();
        var dish=current.get();String reason=item.reason().toLowerCase(Locale.ROOT);
        for(var category:Category.values())if(category!=dish.category()) {
            String label=category.value().toLowerCase(Locale.ROOT);var mentions=Pattern.compile("\\b"+Pattern.quote(label)+"\\b").matcher(reason);
            while(mentions.find()) {
                // "a light meal" describes a meal; it does not claim the catalog category Light Meal.
                if(label.equals("light meal") && !reason.substring(mentions.end()).matches("^ (cuisine|category)\\b.*"))continue;
                String before=reason.substring(0,mentions.start());if(before.matches(".*\\b(from|than|instead of|alternative to|compared (with|to)) (the )?$"))continue;
                throw AiFailure.invalid();
            }
        }
        if(reason.matches(".*\\b(favou?rite|preferred cuisine)\\b.*") && !personal.cuisines().contains(dish.category()))throw AiFailure.invalid();
        if(reason.matches(".*\\b(new|unseen|not recently ordered)\\b.*") && recent)throw AiFailure.invalid();
        if(reason.matches(".*\\b(non[- ]spicy|no spice)\\b.*") && dish.spiceLevel()!=SpiceLevel.None)throw AiFailure.invalid();
        if(reason.matches(".*\\bmild\\b.*") && dish.spiceLevel()!=SpiceLevel.None && dish.spiceLevel()!=SpiceLevel.Mild)throw AiFailure.invalid();
        String heat=reason.replaceAll("\\b(non[- ]spicy|not spicy|no spice)\\b","");
        if(heat.matches(".*\\b(hot|medium|spicy)\\b.*") && dish.spiceLevel()!=SpiceLevel.Medium && dish.spiceLevel()!=SpiceLevel.Hot)throw AiFailure.invalid();
        if(reason.contains("budget") && (reason.contains("within") || reason.contains("fits") || reason.contains("matches") || reason.contains("meets"))) {
            Long maximum=intent.budgetMaxCents()!=null?intent.budgetMaxCents():personal.budgetMaxCents();if(maximum==null || CandidateService.minimumPrice(dish)>maximum)throw AiFailure.invalid();
        }
        var amounts=Pattern.compile("[0-9]+(?:\\.[0-9]+)?").matcher(reason);
        while(amounts.find()) {
            try{if(new BigDecimal(amounts.group()).movePointRight(2).longValueExact()!=CandidateService.minimumPrice(dish))throw AiFailure.invalid();}catch(ArithmeticException invalid){throw AiFailure.invalid();}
            if(!reason.contains("option") || !(reason.contains("change") || reason.contains("vary")))throw AiFailure.invalid();
        }
        return current;
    }
    private static final Pattern UNSUPPORTED=Pattern.compile("(?i)\\b(cures?|treats?|heals?|prevents?|diabetes|medical|healthy|healthier|healthiest|calories?|kcal|vitamins?|nutrients?|nutritious|fat[- ]free|low[- ]fat|low[- ]calorie|high[- ]protein|grams?|guaranteed?|safe|allergen[- ]free|gluten[- ]free|dairy[- ]free|nut[- ]free|peanut[- ]free)\\b");
    public static void validate(RecommendationModels.Generated item,Set<String> allowed,Set<String> seen) {
        try {
            String id=ValueRules.uuid(item.dishId()),reason=item.reason();
            if(reason==null || ValueRules.codePoints(reason)<1 || ValueRules.codePoints(reason)>240 || !reason.matches("(?s).*[A-Za-z].*") || reason.codePoints().anyMatch(c->Character.isLetter(c) && Character.UnicodeScript.of(c)!=Character.UnicodeScript.LATIN)
                || reason.codePoints().anyMatch(Character::isISOControl) || reason.matches("(?s).*[<>\\[\\]`].*") || reason.matches("(?is).*(https?://|www\\.|mailto:).*?") || reason.matches("(?is).*\\b(without|no|free from|does not contain) (gluten|dairy|nuts?|peanuts?|eggs?|soy|fish|shellfish|allergens?)\\b.*") || UNSUPPORTED.matcher(reason).find() || !allowed.contains(id) || !seen.add(id))throw AiFailure.invalid();
        }catch(IllegalArgumentException invalid){throw AiFailure.invalid();}
    }
}
