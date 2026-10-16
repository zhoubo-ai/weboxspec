package com.webox.catalog;
import java.time.LocalDate;
import java.util.List;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.Category;
import com.webox.inventory.SupplyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.webox.catalog.MenuModels.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class MenuService {
    private final MenuQueryRepository query;private final DishRepository dishes;private final SupplyRepository supply;private final MenuDetailsCache cache;private final BusinessClock clock;
    private final com.webox.auth.PreferencesRepository preferences;
    public MenuService(MenuQueryRepository query,DishRepository dishes,SupplyRepository supply,MenuDetailsCache cache,BusinessClock clock,com.webox.auth.PreferencesRepository preferences) { this.query=query;this.dishes=dishes;this.supply=supply;this.cache=cache;this.clock=clock;this.preferences=preferences; }
    public MenuPage page(LocalDate date,List<Category> categories,String search,int page,int size) {
        var result=query.page(date,categories,search,page,size);
        return new MenuPage(date,clock.now(),BusinessClock.ZONE.getId(),supply.menuRevision(date),result.members().stream().map(this::view).toList(),page,size,result.total());
    }
    public MenuDish dish(LocalDate date,String id) { return view(query.member(date,id).orElseThrow(()->new ApiException(404,"NOT_FOUND","This dish is not available on the selected date."))); }
    public MenuPage page(String user,LocalDate date,List<Category> categories,String search,int page,int size,boolean recommended) {
        var personal=preferences.get(user);var result=query.page(date,categories,search,page,size,personal,recommended);
        return new MenuPage(date,clock.now(),BusinessClock.ZONE.getId(),supply.menuRevision(date),result.members().stream().map(member->view(member,personal)).toList(),page,size,result.total());
    }
    public MenuDish dish(String user,LocalDate date,String id) {return view(query.member(date,id).orElseThrow(()->new ApiException(404,"NOT_FOUND","This dish is not available on the selected date.")),preferences.get(user));}
    private MenuDish view(MenuQueryRepository.Member member) {
        return view(member,com.webox.auth.PreferencesModels.Preferences.empty());
    }
    private MenuDish view(MenuQueryRepository.Member member,com.webox.auth.PreferencesModels.Preferences personal) {
        var details=cache.get(member.dishId(),member.dishVersion(),()->dishes.details(member.dishId(),member.dishVersion()));
        return MenuDish.of(details,member.remaining(),member.inventoryVersion(),new PreferenceMatch(personal.cuisines().contains(details.category()),personal.spiceLevel()!=null && personal.spiceLevel()==details.spiceLevel()));
    }
}
