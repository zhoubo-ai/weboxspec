package com.webox.ordering;
import java.time.*;
import java.util.*;
import com.webox.shared.DomainTypes.*;
public final class OrderingModels {
    private OrderingModels() {}
    public record CartItem(String dishId,Long dishVersion,List<String> optionIds,Integer quantity) {}
    public record QuoteRequest(String deliveryDate,Meal meal,List<CartItem> items) {}
    public record OrderCreate(String quoteId,String addressId,String addressText) { @Override public String toString() { return "OrderCreate[redacted]"; } }
    public record OptionSnapshot(String optionId,String groupId,String groupName,String optionName,long extraCents,List<Allergen> allergens) { public OptionSnapshot { allergens=List.copyOf(allergens); } }
    public record PricedLine(String dishId,String dishName,long dishVersion,List<OptionSnapshot> selectedOptions,int quantity,long basePriceCents,long unitPriceCents,long subtotalCents,List<Allergen> effectiveAllergens) {
        public PricedLine { selectedOptions=List.copyOf(selectedOptions);effectiveAllergens=List.copyOf(effectiveAllergens); }
        public String configurationKey() { return IdempotencyRequest.digest(String.join(",",selectedOptions.stream().map(OptionSnapshot::optionId).sorted().toList())); }
    }
    public record Quote(String quoteId,LocalDate deliveryDate,Meal meal,List<PricedLine> items,int totalQuantity,long totalCents,Instant expiresAt,Instant serverTime,List<Map<String,Object>> warnings) { public Quote { items=List.copyOf(items);warnings=List.copyOf(warnings); } }
    public record Order(String orderId,String orderNumber,LocalDate deliveryDate,Meal meal,String addressText,OrderStatus status,List<PricedLine> items,int totalQuantity,long totalCents,long version,Instant createdAt,Instant updatedAt) { public Order { items=List.copyOf(items); } }
    public record OrderPage(List<Order> items,int page,int size,long totalElements) {}
}
