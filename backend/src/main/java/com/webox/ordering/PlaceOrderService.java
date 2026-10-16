package com.webox.ordering;
import java.util.*;
import org.springframework.stereotype.Service;
import com.webox.auth.*;
import com.webox.inventory.InventoryMutationService;
import com.webox.shared.*;
import static com.webox.ordering.OrderingModels.*;
@Service
public class PlaceOrderService {
    private final OperationalMetrics metrics;private final UserRepository users;private final QuoteRepository quotes;private final OrderRepository orders;private final OrderItemRepository items;private final AddressRepository addresses;private final OrderPolicy policy;private final InventoryMutationService inventory;private final OrderTransactionExecutor transactions;private final BusinessClock clock;
    public PlaceOrderService(UserRepository users,QuoteRepository quotes,OrderRepository orders,OrderItemRepository items,AddressRepository addresses,OrderPolicy policy,InventoryMutationService inventory,OrderTransactionExecutor transactions,BusinessClock clock,OperationalMetrics metrics) {this.metrics=metrics; this.users=users;this.quotes=quotes;this.orders=orders;this.items=items;this.addresses=addresses;this.policy=policy;this.inventory=inventory;this.transactions=transactions;this.clock=clock; }
    public record Result(Order order,boolean replayed) {}
    public Result create(String user,String key,OrderCreate body) {
        var request=IdempotencyRequest.normalize(key,body);var existing=orders.byKey(user,request.key());if(existing.isPresent()) {var result=replay(existing.get(),request);metrics.order(true);return result;}
        var result=transactions.execute(()->createLocked(user,request),()->resolveConflict(user,request));metrics.order(result.replayed());return result;
    }
    private Result replay(OrderRepository.Row row,IdempotencyRequest request) {
        if(!row.hash().equals(request.hash())) throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","This request key belongs to a different order request.");return new Result(orders.view(row),true);
    }
    private Result createLocked(String user,IdempotencyRequest request) {
        users.lockOwner(user).orElseThrow(OrderRepository::missing);
        var existing=orders.byKey(user,request.key());if(existing.isPresent()) return replay(existing.get(),request);
        // Also catches a consumed quote whose expired quote row has since been cleaned up.
        if(orders.byQuote(user,request.quoteId()).isPresent()) throw consumed();
        var quote=quotes.lockOwned(request.quoteId(),user).orElseThrow(()->new ApiException(404,"NOT_FOUND","This quote was not found."));
        if(quote.consumedOrderId()!=null) throw consumed();policy.notExpired(quote.expiresAt());policy.quantity(quote.quantity());policy.active(user,quote.date(),quote.meal());
        var lines=quotes.items(quote.id());long total=0;int quantity=0;for(var line:lines) {total=ValueRules.add(total,line.subtotalCents());quantity+=line.quantity();}
        if(lines.isEmpty() || total!=quote.total() || quantity!=quote.quantity()) throw new IllegalStateException("Quote totals are inconsistent.");
        var locked=inventory.lock(quote.date(),lines);inventory.validateForPurchase(locked,lines);
        // Both checks happen after all potentially blocking row locks.
        policy.notExpired(quote.expiresAt());policy.slot(quote.date(),quote.meal());
        String address=request.addressId()==null?request.addressText():addresses.resolve(user,request.addressId());
        inventory.allocate(locked);var now=clock.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);var row=orders.insert(user,request,quote,address,now);
        items.insert(row.id(),lines);addresses.use(user,address,now);quotes.consume(quote.id(),row.id());return new Result(orders.view(row),false);
    }
    private Result resolveConflict(String user,IdempotencyRequest request) {
        var existing=orders.byKey(user,request.key());if(existing.isPresent()) return replay(existing.get(),request);
        if(orders.byQuote(user,request.quoteId()).isPresent()) throw consumed();
        var quote=quotes.findOwned(request.quoteId(),user);if(quote.isPresent()) {if(quote.get().consumedOrderId()!=null) throw consumed();policy.active(user,quote.get().date(),quote.get().meal());}
        throw OrderTransactionExecutor.unavailable();
    }
    private static ApiException consumed() { return new ApiException(409,"QUOTE_CONSUMED","This quote has already been used. Review your existing order or request a new quote."); }
}
