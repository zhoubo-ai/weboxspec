package com.webox.ordering;
import java.time.*;
import java.util.*;
import com.webox.auth.*;
import com.webox.catalog.AdminDishService;
import com.webox.shared.DomainTypes.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.webox.ordering.OrderingModels.*;
@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AdminOrderService {
    private final OrderRepository orders;private final UserRepository users;
    public AdminOrderService(OrderRepository orders,UserRepository users){this.orders=orders;this.users=users;}
    public record AdminOrder(String orderId,String orderNumber,LocalDate deliveryDate,Meal meal,String addressText,OrderStatus status,List<PricedLine> items,int totalQuantity,long totalCents,long version,Instant createdAt,Instant updatedAt,UserIdentity customer) {
        public static AdminOrder of(Order order,UserIdentity customer){return new AdminOrder(order.orderId(),order.orderNumber(),order.deliveryDate(),order.meal(),order.addressText(),order.status(),order.items(),order.totalQuantity(),order.totalCents(),order.version(),order.createdAt(),order.updatedAt(),customer);}
    }
    public record AdminOrderPage(List<AdminOrder> items,int page,int size,long totalElements) {}
    private AdminOrder view(OrderRepository.Row row){return AdminOrder.of(orders.view(row),users.byId(row.userId()).orElseThrow(OrderRepository::missing).identity());}
    public AdminOrder get(UserIdentity actor,String id){AdminDishService.admin(actor);return view(orders.find(id).orElseThrow(OrderRepository::missing));}
    public AdminOrderPage page(UserIdentity actor,LocalDate date,Meal meal,OrderStatus status,int page,int size){AdminDishService.admin(actor);var rows=orders.operationalPage(date,meal,status,page,size);return new AdminOrderPage(rows.rows().stream().map(this::view).toList(),page,size,rows.total());}
}
