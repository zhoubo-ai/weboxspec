package com.webox.ordering;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.*;
import com.webox.catalog.*;
import static com.webox.ordering.OrderingModels.*;
@Service
public class QuoteService {
    private final MenuQueryRepository menu;private final DishRepository dishes;private final QuoteRepository quotes;private final OrderPolicy policy;private final BusinessClock clock;
    private final TransactionTemplate read,write;private final CheckoutWarnings warnings;
    private record Preparation(List<PricedLine> items,List<Map<String,Object>> warnings) {}
    public QuoteService(MenuQueryRepository menu,DishRepository dishes,QuoteRepository quotes,OrderPolicy policy,BusinessClock clock,PlatformTransactionManager manager,CheckoutWarnings warnings) {
        this.warnings=warnings;this.menu=menu;this.dishes=dishes;this.quotes=quotes;this.policy=policy;this.clock=clock;
        read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        write=new TransactionTemplate(manager);write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public Quote create(String user,QuoteRequest request) {
        if(request==null || request.items()==null || request.items().isEmpty() || request.items().size()>5 || request.meal()==null) throw new IllegalArgumentException("Choose 1 to 5 cart lines and a meal.");
        LocalDate date=ValueRules.date(request.deliveryDate());
        for(var item:request.items()) {
            if(item==null || item.dishVersion()==null || item.quantity()==null || item.quantity()<1 || item.quantity()>5 || item.optionIds()==null) throw new IllegalArgumentException("Invalid cart line.");
            ValueRules.uuid(item.dishId());ValueRules.safeInteger(item.dishVersion());var ids=item.optionIds().stream().map(ValueRules::uuid).toList();if(new HashSet<>(ids).size()!=ids.size()) throw new IllegalArgumentException("Duplicate option.");
        }
        int count=request.items().stream().mapToInt(CartItem::quantity).sum();policy.quantity(count);
        var prepared=read.execute(status->{
            policy.slot(date,request.meal());policy.active(user,date,request.meal());var merged=new TreeMap<String,PricedLine>();var demand=new TreeMap<String,Integer>();var available=new HashMap<String,Integer>();
            for(var item:request.items()) {
                String id=ValueRules.uuid(item.dishId());var member=menu.member(date,id).orElseThrow(()->itemError("DISH_UNAVAILABLE",id,"This dish is not available on the selected date.",Map.of()));
                if(member.dishVersion()!=item.dishVersion()) throw itemError("DISH_CHANGED",id,"This dish has changed. Review its current price and options.",Map.of("currentDishVersion",member.dishVersion()));
                var details=dishes.details(id,member.dishVersion());var choices=new TreeSet<>(item.optionIds().stream().map(ValueRules::uuid).toList());var remaining=new HashSet<>(choices);var selected=new ArrayList<OptionSnapshot>();var allergens=new TreeSet<Allergen>(Comparator.comparing(Enum::name));allergens.addAll(details.allergens());long price=details.basePriceCents();
                for(var group:details.optionGroups()) {
                    var options=group.options().stream().filter(option->choices.contains(option.optionId())).toList();
                    if(group.required() && options.isEmpty() || group.selectionMode()==SelectionType.Single && options.size()>1) throw ApiException.validation("items.optionIds","Complete the required choices and select at most one option in each single-choice group.");
                    for(var option:options) { remaining.remove(option.optionId());price=ValueRules.add(price,option.extraCents());allergens.addAll(option.allergens());selected.add(new OptionSnapshot(option.optionId(),group.groupId(),group.name(),option.name(),option.extraCents(),option.allergens())); }
                }
                if(!remaining.isEmpty()) throw ApiException.validation("items.optionIds","An option does not belong to this dish.");
                selected.sort(Comparator.comparing(OptionSnapshot::optionId));var line=new PricedLine(id,details.name(),details.dishVersion(),selected,item.quantity(),details.basePriceCents(),price,ValueRules.multiply(price,item.quantity()),new ArrayList<>(allergens));
                String key=id+":"+line.configurationKey();var previous=merged.get(key);if(previous!=null) line=new PricedLine(id,line.dishName(),line.dishVersion(),selected,previous.quantity()+line.quantity(),line.basePriceCents(),price,ValueRules.multiply(price,previous.quantity()+line.quantity()),line.effectiveAllergens());merged.put(key,line);
                demand.merge(id,item.quantity(),Integer::sum);available.put(id,member.remaining());
            }
            for(var entry:demand.entrySet()) if(entry.getValue()>available.get(entry.getKey())) throw itemError("INSUFFICIENT_STOCK",entry.getKey(),"This dish no longer has enough stock.",Map.of("availableQuantity",available.get(entry.getKey())));
            var items=List.copyOf(merged.values());return new Preparation(items,warnings.forItems(user,items));
        });
        var priced=prepared.items();long total=0;for(var item:priced) total=ValueRules.add(total,item.subtotalCents());Instant now=clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var quote=new Quote(UUID.randomUUID().toString(),date,request.meal(),priced,count,total,now.plusSeconds(300),now,prepared.warnings());
        write.executeWithoutResult(status->{ policy.slot(date,request.meal());quotes.insert(user,quote); });return quote;
    }
    public static ApiException itemError(String code,String dish,String message,Map<String,Object> extra) {
        var issue=new HashMap<String,Object>(extra);issue.put("dishId",dish);issue.put("code",code);issue.put("message",message);
        return new ApiException(409,code,message,false,Map.of("itemIssues",List.of(issue)));
    }
}
