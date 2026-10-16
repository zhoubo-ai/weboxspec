package com.webox.recommendation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.webox.shared.DomainTypes.*;
import com.webox.shared.ValueRules;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

@Service
public class IntentService {
    public record Extracted(@JsonProperty(required=true) String repeatPolicy,@JsonProperty(required=true) String repeatFrom,@JsonProperty(required=true) String repeatThrough,@JsonProperty(required=true) String repeatEvidence,@JsonProperty(required=true) Boolean needsClarification,
        @JsonProperty(required=true) List<Category> cuisines,@JsonProperty(required=true) SpiceLevel spiceLevel,@JsonProperty(required=true) Flavor flavor,@JsonProperty(required=true) Long budgetMaxCents,@JsonProperty(required=true) String protein,@JsonProperty(required=true) List<Allergen> excludedAllergens,@JsonProperty(required=true) String dishQuery) {}
    public record Intent(Set<Category> cuisines,SpiceLevel spiceLevel,Flavor flavor,Long budgetMaxCents,String protein,Set<Allergen> excludedAllergens,LocalDate repeatFrom,LocalDate repeatThrough,boolean hardRepeat,String dishQuery) {
        public Intent {cuisines=Set.copyOf(cuisines);excludedAllergens=Set.copyOf(excludedAllergens);}
        public static Intent none(){return new Intent(Set.of(),null,null,null,null,Set.of(),null,null,false,null);}
        public static Intent exclude(LocalDate from,LocalDate through){return new Intent(Set.of(),null,null,null,null,Set.of(),from,through,true,null);}
    }
    private static final Pattern EXPLICIT=Pattern.compile("(?i)\\b(no repeats?|no repeated|do not repeat|don't repeat|avoid repeating|without repeats?|no dishes I (?:ate|had)|avoid (?:anything|meals?|dishes?) I (?:ate|had))\\b");
    private static final Pattern DATES=Pattern.compile("(?<![0-9])[0-9]{4}-[0-9]{2}-[0-9]{2}(?![0-9])");
    private static final Pattern DAYS=Pattern.compile("\\b(?:last|past|previous)\\s+(\\d+)\\s+days?\\b");
    public static Set<Allergen> mentionedAllergens(String prompt) {
        var labels=EnumSet.noneOf(Allergen.class);String text=prompt.toLowerCase(Locale.ROOT);
        var words=Map.of(Allergen.Peanuts,"\\b(peanuts?|groundnuts?)\\b|花生",Allergen.Dairy,"\\b(dairy|milk|lactose|cheese)\\b|乳|奶|芝士",Allergen.Egg,"\\beggs?\\b|蛋",Allergen.Gluten,"\\b(gluten|wheat)\\b|麸质|面筋|小麦",Allergen.Soy,"\\b(soy|soya|soybeans?)\\b|大豆|豆制品|豆乳",Allergen.Fish,"\\bfish\\b|鱼",Allergen.Shellfish,"\\b(shellfish|shrimp|prawns?|crabs?|lobster|clams?|mussels?|oysters?)\\b|甲壳|贝类|虾|蟹");
        words.forEach((label,expression)->{if(Pattern.compile(expression).matcher(text).find())labels.add(label);});return labels;
    }
    public Intent normalize(String prompt,LocalDate today,Extracted value) {
        if(value==null || value.needsClarification()==null || value.repeatPolicy()==null || value.cuisines()==null || value.excludedAllergens()==null || !Set.of("DEFAULT","EXCLUDE").contains(value.repeatPolicy()))throw AiFailure.invalid();
        if(value.needsClarification())throw AiFailure.clarification();
        if(value.cuisines().stream().anyMatch(Objects::isNull) || value.excludedAllergens().stream().anyMatch(Objects::isNull) || new HashSet<>(value.cuisines()).size()!=value.cuisines().size() || new HashSet<>(value.excludedAllergens()).size()!=value.excludedAllergens().size())throw AiFailure.invalid();
        if(!mentionedAllergens(prompt).containsAll(value.excludedAllergens()))throw AiFailure.invalid();
        try {if(value.budgetMaxCents()!=null)ValueRules.safeInteger(value.budgetMaxCents());if(value.protein()!=null)ValueRules.requiredText(value.protein(),50);if(value.dishQuery()!=null)ValueRules.requiredText(value.dishQuery(),100);}catch(IllegalArgumentException invalid){throw AiFailure.invalid();}
        boolean hard="EXCLUDE".equals(value.repeatPolicy());LocalDate from=null,through=null;
        if(!hard && EXPLICIT.matcher(prompt).find())throw AiFailure.clarification();
        if(hard) {
            String evidence=value.repeatEvidence();if(evidence==null || evidence.isBlank() || !prompt.toLowerCase(Locale.ROOT).contains(evidence.toLowerCase(Locale.ROOT)))throw AiFailure.clarification();
            LocalDate[] range=range(prompt,today);from=range[0];through=range[1];
            try {
                if(from.isAfter(through) || through.isAfter(today) || from.getYear()<1 || (value.repeatFrom()!=null && !from.equals(ValueRules.date(value.repeatFrom()))) || (value.repeatThrough()!=null && !through.equals(ValueRules.date(value.repeatThrough()))))throw AiFailure.clarification();
            }catch(IllegalArgumentException invalid){throw AiFailure.clarification();}
        } else if(value.repeatFrom()!=null || value.repeatThrough()!=null || value.repeatEvidence()!=null)throw AiFailure.invalid();
        return new Intent(new HashSet<>(value.cuisines()),value.spiceLevel(),value.flavor(),value.budgetMaxCents(),value.protein()==null?null:value.protein().strip(),new HashSet<>(value.excludedAllergens()),from,through,hard,value.dishQuery()==null?null:value.dishQuery().strip());
    }
    private LocalDate[] range(String evidence,LocalDate today) {
        String text=evidence.toLowerCase(Locale.ROOT);var dates=new ArrayList<LocalDate>();var matcher=DATES.matcher(text);
        try {
            while(matcher.find())dates.add(ValueRules.date(matcher.group()));
            if(dates.size()>2)throw AiFailure.clarification();if(dates.size()==2)return new LocalDate[]{dates.get(0),dates.get(1)};if(dates.size()==1)return new LocalDate[]{dates.get(0),dates.get(0)};
            var days=DAYS.matcher(text);if(days.find()){long count=Long.parseLong(days.group(1));if(count<1 || days.find())throw AiFailure.clarification();return new LocalDate[]{today.minusDays(count-1),today};}
            if(text.contains("yesterday"))return new LocalDate[]{today.minusDays(1),today.minusDays(1)};
            if(text.contains("today"))return new LocalDate[]{today,today};
            if(text.contains("last month") || text.contains("previous month"))return new LocalDate[]{today.withDayOfMonth(1).minusMonths(1),today.withDayOfMonth(1).minusDays(1)};
            if(text.contains("this month"))return new LocalDate[]{today.withDayOfMonth(1),today};
            if(text.contains("last week") || text.contains("past week") || text.contains("previous week"))return new LocalDate[]{today.minusDays(6),today};
            if(text.contains("this week"))return new LocalDate[]{today.minusDays(today.getDayOfWeek().getValue()-1),today};
            // A period was supplied but cannot be interpreted: never silently use only seven days.
            if(Pattern.compile("\\b(days?|weeks?|weekends?|months?|years?|seasons?|fortnights?|time|since|from|before|after|between|ago|ever|earlier)\\b").matcher(text).find() || text.codePoints().anyMatch(c->Character.isLetter(c) && Character.UnicodeScript.of(c)!=Character.UnicodeScript.LATIN))throw AiFailure.clarification();
            return new LocalDate[]{today.minusDays(6),today};
        }catch(DateTimeException | IllegalArgumentException invalid){throw AiFailure.clarification();}
    }
}
