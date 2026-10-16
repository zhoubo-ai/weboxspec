package com.webox.auth;
import java.util.*;
import com.webox.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.webox.auth.PreferencesModels.*;
@Service
public class PreferencesService {
    private final PreferencesRepository preferences;private final UserRepository users;private final BusinessClock clock;
    public PreferencesService(PreferencesRepository preferences,UserRepository users,BusinessClock clock) {this.preferences=preferences;this.users=users;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preferences get(String user) { return preferences.get(user); }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Preferences replace(String user,Write body) {
        if(body==null || body.allergens()==null || body.cuisines()==null || body.expectedVersion()==null) throw new IllegalArgumentException("Provide all preference fields.");
        collection(body.allergens(),7);collection(body.cuisines(),6);ValueRules.safeInteger(body.expectedVersion());
        if((body.budgetMinCents()==null)!=(body.budgetMaxCents()==null)) throw ApiException.validation("budgetMaxCents","Set both budget limits, or clear both.");
        if(body.budgetMinCents()!=null) {ValueRules.safeInteger(body.budgetMinCents());ValueRules.safeInteger(body.budgetMaxCents());if(body.budgetMinCents()>body.budgetMaxCents())throw ApiException.validation("budgetMaxCents","Maximum budget must be at least the minimum budget.");}
        users.lockOwner(user).orElseThrow(()->new ApiException(401,"UNAUTHENTICATED","Please sign in."));var previous=preferences.find(user,true);long version=previous.map(Preferences::version).orElse(0L);
        if(version!=body.expectedVersion()) throw new ApiException(409,"VERSION_CONFLICT","Preferences changed in another session. Reload and review before saving.");
        var saved=new Preferences(body.allergens().stream().sorted(Comparator.comparing(Enum::name)).toList(),body.cuisines().stream().sorted(Comparator.comparing(com.webox.shared.DomainTypes.Category::value)).toList(),body.spiceLevel(),body.flavor(),body.budgetMinCents(),body.budgetMaxCents(),ValueRules.add(version,1));
        preferences.replace(user,saved,previous.isPresent(),clock.now());return saved;
    }
    private static void collection(List<?> values,int max) {if(values.size()>max || values.stream().anyMatch(Objects::isNull) || new HashSet<>(values).size()!=values.size())throw new IllegalArgumentException("Use unique supported preferences.");}
}
