package com.webox.ordering;
import com.webox.support.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class OrderRulesIT extends OrderingTestSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.webox.catalog.DishRepository dishes;
    @Test void exactShanghaiCutoffsPastAndFutureDatesFollowTheSameServerPolicy() throws Exception {
        var user=browser();var menu=menu();
        for(String boundary:List.of("09:59:59","10:00:00","14:59:59","15:00:00","23:59:59")) {
            TEST_CLOCK.set(menu.date().atTime(LocalTime.parse(boundary)).atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            for(String meal:List.of("Lunch","Dinner")) {
                var request=new HashMap<String,Object>(request(menu,1));request.put("meal",meal);var result=postJson("/checkout/quotes",user,request,null);
                boolean closed=LocalTime.parse(boundary).compareTo(LocalTime.of(meal.equals("Lunch")?10:15,0))>=0;
                if(!closed) {assertEquals(201,result.getStatus());assertEquals(menu.date().toString(),field(result,"deliveryDate"));assertEquals(meal,field(result,"meal"));}
                else {error(result,409,"SLOT_EXPIRED");boolean dinner=boundary.compareTo("15:00:00")<0;assertEquals(dinner?menu.date().toString():menu.date().plusDays(1).toString(),field(result,"suggestedDeliveryDate"));assertEquals(dinner?"Dinner":"Lunch",field(result,"suggestedMeal"));}
            }
        }
        TEST_CLOCK.set(menu.date().minusDays(2).atTime(18,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant());var future=postJson("/checkout/quotes",user,request(menu,1),null);assertEquals(201,future.getStatus());assertEquals(menu.date().toString(),field(future,"deliveryDate"));
        TEST_CLOCK.set(menu.date().plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());error(postJson("/checkout/quotes",user,request(menu,1),null),422,"INVALID_DELIVERY_DATE");
    }
    @Test void zeroOneFiveSixAndFractionalQuantitiesCannotBypassServerRules() throws Exception {
        var user=browser();var menu=menu();
        for(int quantity:List.of(0,1,5,6)) {var result=postJson("/checkout/quotes",user,request(menu,quantity),null);if(quantity==1||quantity==5)assertEquals(201,result.getStatus());else error(result,400,"VALIDATION_ERROR");}
        var six=new HashMap<String,Object>(request(menu,1));six.put("items",List.of(line(menu.pasta(),1,3,menu.options().get("Spaghetti")),line(menu.pasta(),1,3,menu.options().get("Fusilli"))));error(postJson("/checkout/quotes",user,six,null),422,"ORDER_LIMIT_EXCEEDED");
        var fraction=new HashMap<String,Object>(request(menu,1));fraction.put("items",List.of(Map.of("dishId",menu.pasta(),"dishVersion",1,"quantity",1.1,"optionIds",List.of(menu.options().get("Spaghetti")))));error(postJson("/checkout/quotes",user,fraction,null),400,"VALIDATION_ERROR");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));
    }
    @Test void aQuoteReadCrossingTheCutoffDoesNotReturnANewExpiredSlot() throws Exception {
        var user=browser();var menu=menu();TEST_CLOCK.set(menu.date().atTime(9,59,59).atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        org.mockito.Mockito.doAnswer(invocation->{var result=invocation.callRealMethod();TEST_CLOCK.set(menu.date().atTime(10,0).atZone(ZoneId.of("Asia/Shanghai")).toInstant());return result;}).when(dishes).details(org.mockito.ArgumentMatchers.eq(menu.pasta()),org.mockito.ArgumentMatchers.eq(1L));
        try {error(postJson("/checkout/quotes",user,request(menu,1),null),409,"SLOT_EXPIRED");assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM checkout_quotes WHERE user_id=?",Integer.class,user.identity().user().userId()));}
        finally {org.mockito.Mockito.reset(dishes);}
    }
}
