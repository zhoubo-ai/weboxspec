package com.webox.reporting;
import com.webox.support.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class DashboardContractIT extends AdminTestSupport {
    @Test void onlyAdministratorsCanReadTheDashboard()throws Exception {
        error(mvc.perform(get("/api/v1/admin/dashboard")).andReturn().getResponse(),401,"UNAUTHENTICATED");error(read("/admin/dashboard",browser()),403,"FORBIDDEN");assertEquals(200,read("/admin/dashboard?date=2099-01-01",admin()).getStatus());
    }
    @Test void omittedDateUsesShanghaiTodayAndEmptyResultsHaveSevenDaysAndBothMeals()throws Exception {
        var actor=admin();TEST_CLOCK.set(Instant.parse("2055-01-01T16:05:00Z"));var response=read("/admin/dashboard",actor);assertEquals(200,response.getStatus());ContractAssertions.matches("Dashboard",responseBody(response));var value=json.readTree(responseBody(response));assertEquals("2055-01-02",value.path("date").asString());assertEquals("2055-01-01T16:05:00Z",value.path("generatedAt").asString());
        for(String field:List.of("totalOrders","revenueCents","completedOrders","pendingOrders","cancelledOrders"))assertEquals(0,value.path(field).asLong());assertEquals(0,value.path("topDishes").size());assertEquals(0,value.path("inventoryAlerts").size());assertEquals(2,value.path("mealDistribution").size());assertEquals(Set.of("Lunch","Dinner"),new HashSet<>(List.of(value.path("mealDistribution").get(0).path("meal").asString(),value.path("mealDistribution").get(1).path("meal").asString())));
        assertEquals(7,value.path("trend").size());for(int i=0;i<7;i++){var day=value.path("trend").get(i);assertEquals(LocalDate.of(2055,1,2).minusDays(6-i).toString(),day.path("date").asString());assertEquals(0,day.path("orderCount").asLong());assertEquals(0,day.path("revenueCents").asLong());}assertTrue(value.path("countingPolicy").asString().contains("Completed Order Amount"));
    }
    @Test void explicitPastAndFutureDatesOverrideTodayButMalformedCalendarDatesNeverDefault()throws Exception {
        var actor=admin();for(String date:List.of("1970-01-01","2099-12-31")){var response=read("/admin/dashboard?date="+date,actor);assertEquals(200,response.getStatus());ContractAssertions.matches("Dashboard",responseBody(response));assertEquals(date,field(response,"date"));}
        for(String date:List.of("","2026-02-30","2026-1-01","not-a-date","2026-10-16T00:00:00Z"))error(read("/admin/dashboard?date="+date,actor),400,"VALIDATION_ERROR");
    }
}
