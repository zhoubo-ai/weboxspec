package com.webox.ordering;
import java.util.Set;
import com.webox.auth.UserIdentity;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.webox.ordering.AdminOrderService.*;
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final AdminOrderService orders;private final OrderStatusService statuses;
    public AdminOrderController(AdminOrderService orders,OrderStatusService statuses){this.orders=orders;this.statuses=statuses;}
    public record StatusChange(OrderStatus status,Long expectedVersion) {}
    @GetMapping public AdminOrderPage page(@AuthenticationPrincipal UserIdentity actor,@RequestParam(required=false) String deliveryDate,@RequestParam(required=false) Meal meal,@RequestParam(required=false) OrderStatus status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){if(page<0 || size<1 || size>100)throw new IllegalArgumentException("Invalid order page.");return orders.page(actor,deliveryDate==null?null:ValueRules.date(deliveryDate),meal,status,page,size);}
    @GetMapping("/{orderId}") public AdminOrder get(@AuthenticationPrincipal UserIdentity actor,@PathVariable String orderId){return orders.get(actor,ValueRules.uuid(orderId));}
    @PatchMapping("/{orderId}/status") public AdminOrder change(@AuthenticationPrincipal UserIdentity actor,@PathVariable String orderId,@RequestBody StatusChange body){
        if(body==null || body.status()==null || body.expectedVersion()==null || !Set.of(OrderStatus.Confirmed,OrderStatus.Completed,OrderStatus.Cancelled).contains(body.status()))throw new IllegalArgumentException("Choose a valid order transition and expected version.");ValueRules.safeInteger(body.expectedVersion());String id=ValueRules.uuid(orderId);statuses.administer(actor,id,body.status(),body.expectedVersion());return orders.get(actor,id);
    }
}
