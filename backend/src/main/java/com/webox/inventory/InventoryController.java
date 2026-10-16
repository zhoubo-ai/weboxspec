package com.webox.inventory;
import com.webox.shared.ValueRules;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.*;
@RestController @ConditionalOnWebApplication
@RequestMapping("/api/v1/menus/{date}")
public class InventoryController {
    private final InventoryQuery inventory;private final InventorySubscriptions subscriptions;
    public InventoryController(InventoryQuery inventory,InventorySubscriptions subscriptions){this.inventory=inventory;this.subscriptions=subscriptions;}
    @GetMapping("/inventory") public InventoryModels.Snapshot snapshot(@PathVariable String date){return inventory.snapshot(ValueRules.date(date));}
    @GetMapping(value="/events",produces="text/event-stream") public void events(@PathVariable String date,HttpServletRequest request,HttpServletResponse response) throws IOException {
        var day=ValueRules.date(date);var connection=subscriptions.register(day,request.getSession(false).getId());
        try {
            var snapshot=inventory.snapshot(day);response.setContentType("text/event-stream");response.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");response.setHeader("X-Accel-Buffering","no");
            connection.begin(snapshot,request.startAsync(),response);
        }catch(IOException | RuntimeException failure){connection.close();throw failure;}
    }
}
