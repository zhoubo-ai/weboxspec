package com.webox.ordering;
import java.util.Map;
import com.webox.auth.UserIdentity;
import com.webox.shared.ValueRules;
import com.webox.shared.DomainTypes.OrderStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final PlaceOrderService placement;private final OrderRepository orders;private final OrderStatusService statuses;
    public OrderController(PlaceOrderService placement,OrderRepository orders,OrderStatusService statuses) { this.placement=placement;this.orders=orders;this.statuses=statuses; }
    @PostMapping public ResponseEntity<OrderingModels.Order> create(@AuthenticationPrincipal UserIdentity user,@RequestHeader("Idempotency-Key") String key,@RequestBody OrderingModels.OrderCreate request) {
        var result=placement.create(user.userId(),key,request);return ResponseEntity.status(result.replayed()?200:201).header("Idempotency-Replayed",Boolean.toString(result.replayed())).body(result.order());
    }
    @GetMapping public OrderingModels.OrderPage list(@AuthenticationPrincipal UserIdentity user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size,@RequestParam(required=false) OrderStatus status) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid pagination.");return orders.page(user.userId(),status,page,size);
    }
    @GetMapping("/{orderId}") public OrderingModels.Order get(@AuthenticationPrincipal UserIdentity user,@PathVariable String orderId) { return orders.owned(ValueRules.uuid(orderId),user.userId()); }
    @PostMapping("/{orderId}/cancel") public OrderingModels.Order cancel(@AuthenticationPrincipal UserIdentity user,@PathVariable String orderId,@RequestBody(required=false) Map<String,Object> body) {
        if(body!=null && !body.isEmpty()) throw new IllegalArgumentException("Cancellation does not accept additional fields.");return statuses.cancel(user.userId(),ValueRules.uuid(orderId));
    }
}
