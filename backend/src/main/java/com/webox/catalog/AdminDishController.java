package com.webox.catalog;
import java.util.*;
import com.webox.auth.UserIdentity;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.Category;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.webox.catalog.AdminCatalogModels.*;
@RestController
@RequestMapping("/api/v1/admin/dishes")
public class AdminDishController {
    private final AdminDishService dishes;
    public AdminDishController(AdminDishService dishes){this.dishes=dishes;}
    @GetMapping public AdminDishPage page(@RequestParam(defaultValue="") List<String> categories,@RequestParam(defaultValue="") String search,@RequestParam(required=false) String published,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size) {
        if(page<0 || size<1 || size>100 || categories.size()>6 || new HashSet<>(categories).size()!=categories.size() || ValueRules.codePoints(search)>50 || published!=null && !Set.of("true","false").contains(published))throw new IllegalArgumentException("Invalid dish query.");return dishes.page(categories.stream().map(Category::parse).toList(),search.strip(),published==null?null:Boolean.valueOf(published),page,size);
    }
    @GetMapping("/{dishId}") public AdminDish get(@PathVariable String dishId){return dishes.get(ValueRules.uuid(dishId));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public AdminDish create(@AuthenticationPrincipal UserIdentity actor,@RequestBody DishCreate body){return dishes.create(actor,body);}
    @PutMapping("/{dishId}") public AdminDish update(@AuthenticationPrincipal UserIdentity actor,@PathVariable String dishId,@RequestBody DishUpdate body){return dishes.update(actor,ValueRules.uuid(dishId),body);}
    @PatchMapping("/{dishId}/publication") public AdminDish publish(@AuthenticationPrincipal UserIdentity actor,@PathVariable String dishId,@RequestBody Publication body){return dishes.publish(actor,ValueRules.uuid(dishId),body);}
}
