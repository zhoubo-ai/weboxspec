package com.webox.catalog;
import com.webox.auth.UserIdentity;
import com.webox.shared.ValueRules;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.webox.catalog.AdminCatalogModels.*;
@RestController
@RequestMapping("/api/v1/admin/menus")
public class AdminDailyMenuController {
    private final AdminDailyMenuService menus;
    public AdminDailyMenuController(AdminDailyMenuService menus){this.menus=menus;}
    @GetMapping("/{date}") public DailyMenu get(@PathVariable String date){return menus.get(ValueRules.date(date));}
    @PutMapping("/{date}") public DailyMenu replace(@AuthenticationPrincipal UserIdentity actor,@PathVariable String date,@RequestBody DailyMenuWrite body){return menus.replace(actor,ValueRules.date(date),body);}
}
