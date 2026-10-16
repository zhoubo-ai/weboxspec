package com.webox.catalog;
import java.util.*;
import com.webox.shared.ValueRules;
import com.webox.shared.DomainTypes.Category;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {
    private final MenuService menus;
    public MenuController(MenuService menus) { this.menus=menus; }
    @GetMapping("/{date}") public MenuModels.MenuPage page(@org.springframework.security.core.annotation.AuthenticationPrincipal com.webox.auth.UserIdentity user,@PathVariable String date,@RequestParam(defaultValue="") List<String> categories,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="false") String recommended,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size) {
        if(page<0 || size<1 || size>100 || categories.size()>6 || new HashSet<>(categories).size()!=categories.size() || !Set.of("true","false").contains(recommended)) throw new IllegalArgumentException("Invalid menu query.");
        if(ValueRules.codePoints(search)>50) throw new IllegalArgumentException("Search is too long.");
        return menus.page(user.userId(),ValueRules.date(date),categories.stream().map(Category::parse).toList(),search.strip(),page,size,Boolean.parseBoolean(recommended));
    }
    @GetMapping("/{date}/dishes/{dishId}") public MenuModels.MenuDish dish(@org.springframework.security.core.annotation.AuthenticationPrincipal com.webox.auth.UserIdentity user,@PathVariable String date,@PathVariable String dishId) { return menus.dish(user.userId(),ValueRules.date(date),ValueRules.uuid(dishId)); }
}
