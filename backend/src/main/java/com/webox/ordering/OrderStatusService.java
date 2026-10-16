package com.webox.ordering;
import org.springframework.stereotype.Service;
import com.webox.auth.*;
import com.webox.inventory.InventoryMutationService;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.*;
import static com.webox.ordering.OrderingModels.*;
@Service
public class OrderStatusService {
    private final UserRepository users;private final OrderRepository orders;private final OrderItemRepository items;private final InventoryMutationService inventory;private final OrderTransactionExecutor transactions;private final BusinessClock clock;
    public OrderStatusService(UserRepository users,OrderRepository orders,OrderItemRepository items,InventoryMutationService inventory,OrderTransactionExecutor transactions,BusinessClock clock) {this.users=users;this.orders=orders;this.items=items;this.inventory=inventory;this.transactions=transactions;this.clock=clock;}
    public Order cancel(String user,String id) { return change(user,id,OrderStatus.Cancelled,null,false); }
    public Order administer(UserIdentity actor,String id,OrderStatus target,long expectedVersion) {
        if(actor.role()!=Role.Admin) throw new ApiException(403,"FORBIDDEN","Administrator access is required.");ValueRules.safeInteger(expectedVersion);return change(actor.userId(),id,target,expectedVersion,true);
    }
    private Order change(String actor,String id,OrderStatus target,Long version,boolean administrator) {
        var located=orders.find(id).orElseThrow(OrderRepository::missing);String owner=located.userId();if(!administrator && !owner.equals(actor)) throw OrderRepository.missing();
        return transactions.execute(()->{
            users.lockOwner(owner).orElseThrow(OrderRepository::missing);var order=orders.lock(id,owner);
            if(order.status()==OrderStatus.Cancelled && target==OrderStatus.Cancelled) return orders.view(order);
            if(administrator && order.version()!=version) throw new ApiException(409,"VERSION_CONFLICT","This order has changed. Reload its current status.");
            boolean allowed=order.status()==OrderStatus.Pending && (target==OrderStatus.Confirmed || target==OrderStatus.Cancelled) || order.status()==OrderStatus.Confirmed && target==OrderStatus.Completed;
            if(!allowed) throw new ApiException(409,administrator?"INVALID_ORDER_TRANSITION":"ORDER_NOT_CANCELLABLE",administrator?"This order status change is not allowed.":"Only a pending order can be cancelled.");
            if(target==OrderStatus.Cancelled) inventory.restore(inventory.lock(order.date(),items.list(order.id())));
            orders.transition(order,target,clock.now());return orders.owned(id,owner);
        },null);
    }
}
