package com.webox.ordering;
import java.time.*;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.Meal;
@Component
public class OrderPolicy {
    private final BusinessClock clock;private final OrderRepository orders;
    public OrderPolicy(BusinessClock clock,OrderRepository orders) { this.clock=clock;this.orders=orders; }
    public void quantity(long total) { if(total<1 || total>5) throw new ApiException(422,"ORDER_LIMIT_EXCEEDED","An order must contain 1 to 5 portions."); }
    public void slot(LocalDate date,Meal meal) {
        if(date==null || meal==null) throw new IllegalArgumentException("Select a delivery date and meal.");
        Instant now=clock.now();LocalDate today=now.atZone(BusinessClock.ZONE).toLocalDate();
        if(date.isBefore(today)) throw new ApiException(422,"INVALID_DELIVERY_DATE","Choose today or a future delivery date.");
        if(!now.isBefore(clock.cutoff(date,meal))) {
            boolean dinner=now.isBefore(clock.cutoff(today,Meal.Dinner));
            throw new ApiException(409,"SLOT_EXPIRED","This meal has closed. Confirm the suggested delivery date and meal before continuing.",false,Map.of("suggestedDeliveryDate",dinner?today:today.plusDays(1),"suggestedMeal",dinner?Meal.Dinner:Meal.Lunch));
        }
    }
    public void active(String user,LocalDate date,Meal meal) { orders.active(user,date,meal).ifPresent(id->{throw new ApiException(409,"ACTIVE_ORDER_EXISTS","You already have an active order for this meal.",false,Map.of("existingOrderId",id));}); }
    public void notExpired(Instant expires) { if(!clock.now().isBefore(expires)) throw new ApiException(409,"QUOTE_EXPIRED","This quote has expired. Review your cart and request a new quote."); }
}
