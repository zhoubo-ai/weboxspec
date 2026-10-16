package com.webox.reporting;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.webox.auth.UserIdentity;
import com.webox.catalog.AdminDishService;
import com.webox.shared.BusinessClock;
import com.webox.shared.DomainTypes.Meal;
import static com.webox.reporting.DashboardModels.*;
@Service
public class DashboardService {
    private final DashboardRepository repository;private final BusinessClock clock;
    public DashboardService(DashboardRepository repository,BusinessClock clock){this.repository=repository;this.clock=clock;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Dashboard get(UserIdentity actor,LocalDate requestedDate) {
        AdminDishService.admin(actor);LocalDate date=requestedDate==null?clock.today():requestedDate;
        var summary=repository.summary(date);var top=repository.topDishes(date);
        var indexed=repository.trend(date).stream().collect(Collectors.toMap(Day::date,Function.identity()));var trend=new ArrayList<Day>(7);
        for(int offset=6;offset>=0;offset--){LocalDate day=date.minusDays(offset);trend.add(indexed.getOrDefault(day,new Day(day,0,0)));}
        var alerts=repository.inventory(date);
        return new Dashboard(date,clock.now(),summary.totalOrders(),summary.revenueCents(),summary.completedOrders(),summary.pendingOrders(),summary.cancelledOrders(),top,List.of(new MealCount(Meal.Lunch,summary.lunch()),new MealCount(Meal.Dinner,summary.dinner())),List.copyOf(trend),alerts,
            "All figures use delivery dates in Asia/Shanghai. Order and meal counts include all statuses; pending means Pending + Confirmed. Completed Order Amount includes add-ons from Completed orders; it is not payment received. Top dishes count completed portions, merging configurations and using names at purchase. The trend covers the selected date and its six preceding days. Inventory alerts show the current remaining stock for scheduled, published dishes on the selected date, not historical stock.");
    }
}
