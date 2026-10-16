package com.webox.reporting;
import java.time.*;
import java.util.List;
import com.webox.shared.DomainTypes.Meal;
public final class DashboardModels {
    private DashboardModels() {}
    public record Summary(long totalOrders,long revenueCents,long completedOrders,long pendingOrders,long cancelledOrders,long lunch,long dinner) {}
    public record TopDish(String dishId,String name,long quantity) {}
    public record MealCount(Meal meal,long orderCount) {}
    public record Day(LocalDate date,long orderCount,long revenueCents) {}
    public record InventoryAlert(String dishId,String name,long remaining,String level) {}
    public record Dashboard(LocalDate date,Instant generatedAt,long totalOrders,long revenueCents,long completedOrders,long pendingOrders,long cancelledOrders,List<TopDish> topDishes,List<MealCount> mealDistribution,List<Day> trend,List<InventoryAlert> inventoryAlerts,String countingPolicy) {}
}
