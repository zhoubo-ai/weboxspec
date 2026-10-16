package com.webox.reporting;
import com.webox.auth.UserIdentity;
import com.webox.shared.ValueRules;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service){this.service=service;}
    @GetMapping public DashboardModels.Dashboard get(@AuthenticationPrincipal UserIdentity actor,@RequestParam(required=false) String date){return service.get(actor,date==null?null:ValueRules.date(date));}
}
