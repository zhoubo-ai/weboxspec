package com.webox.shared;
import java.time.*;
import org.springframework.stereotype.Component;
import com.webox.shared.DomainTypes.Meal;
@Component
public final class BusinessClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final Clock clock;
    public BusinessClock(Clock clock) { this.clock = clock; }
    public Instant now() { return clock.instant(); }
    public LocalDate today() { return now().atZone(ZONE).toLocalDate(); }
    public Instant cutoff(LocalDate date, Meal meal) {
        return date.atTime(meal == Meal.Lunch ? 10 : 15, 0).atZone(ZONE).toInstant();
    }
}
