package com.webox.reporting;
import com.webox.support.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class DashboardAggregationIT extends AdminTestSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean DashboardRepository repository;
    @Test void oneReadOnlyRepeatableReadSnapshotSurvivesACommitBetweenQueries()throws Exception {
        var fixture=fixture();var actor=admin();var once=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call->{
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ,org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            Object summary=call.callRealMethod();
            if(once.compareAndSet(false,true)) {
                var write=new org.springframework.transaction.support.TransactionTemplate(manager);write.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                write.executeWithoutResult(status->{
                    jdbc.update("UPDATE orders SET status='Completed',confirmed_at=created_at,completed_at=created_at,version=version+1 WHERE id=?",fixture.orders().get("o9"));
                    jdbc.update("UPDATE daily_supply SET total_quantity=total_quantity-1 WHERE delivery_date=? AND dish_id=?",fixture.date(),fixture.dishes().get("A"));
                });
            }return summary;
        }).when(repository).summary(fixture.date());
        try {
            var first=read("/admin/dashboard?date="+fixture.date(),actor);assertEquals(200,first.getStatus());var snapshot=(ObjectNode)json.readTree(responseBody(first));snapshot.remove("generatedAt");snapshot.remove("countingPolicy");assertEquals(expected(fixture,"D"),snapshot);
            var next=json.readTree(responseBody(read("/admin/dashboard?date="+fixture.date(),actor)));assertEquals(11300,next.path("revenueCents").asLong());assertEquals(4,next.path("completedOrders").asLong());assertEquals(1,next.path("pendingOrders").asLong());assertEquals(3,next.path("topDishes").size());assertEquals("Garden Greens",next.path("topDishes").get(2).path("name").asString());assertEquals(2,next.path("inventoryAlerts").get(1).path("remaining").asLong());
        }finally {org.mockito.Mockito.reset(repository);}
    }
    @Test void topTenTiesAreStableAndCompletedOrdersCanBeReorderedWithoutRepricingHistory()throws Exception {
        var menu=menu();var buyer=browser();var actor=admin();var catalog=new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,manager);var ids=new ArrayList<String>();
        TEST_CLOCK.set(menu.date().atTime(8,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());
        for(int i=0;i<12;i++) {
            String dish=catalog.dish(menu.date(),"Ranked dish "+i,"Plain rice.",100,"Chinese","Rice",menu.mediaId(),true,true,20);ids.add(dish);
            var quote=postJson("/checkout/quotes",buyer,Map.of("deliveryDate",menu.date().toString(),"meal","Lunch","items",List.of(line(dish,1,1))),null);assertEquals(201,quote.getStatus());var order=postJson("/orders",buyer,Map.of("quoteId",field(quote,"quoteId"),"addressText","Ranking office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());String id=field(order,"orderId");
            assertEquals(200,write("PATCH","/admin/orders/"+id+"/status",actor,Map.of("status","Confirmed","expectedVersion",1)).getStatus());assertEquals(200,write("PATCH","/admin/orders/"+id+"/status",actor,Map.of("status","Completed","expectedVersion",2)).getStatus());
            jdbc.update("UPDATE dishes SET name='New current name',base_price_cents=99999,version=version+1 WHERE id=?",dish);
        }
        var result=json.readTree(responseBody(read("/admin/dashboard?date="+menu.date(),actor)));assertEquals(1200,result.path("revenueCents").asLong());assertEquals(12,result.path("completedOrders").asLong());Collections.sort(ids);assertEquals(10,result.path("topDishes").size());for(int i=0;i<10;i++){var row=result.path("topDishes").get(i);assertEquals(ids.get(i),row.path("dishId").asString());assertEquals(1,row.path("quantity").asLong());assertTrue(row.path("name").asString().startsWith("Ranked dish "));}
    }
    @Test void aggregateBeyondSafeIntegerFailsInsteadOfRoundingOrWrapping()throws Exception {
        var fixture=fixture();var actor=admin();jdbc.update("UPDATE orders SET total_cents=9007199254740991 WHERE id=?",fixture.orders().get("o6"));
        var response=read("/admin/dashboard?date="+fixture.date(),actor);assertEquals(500,response.getStatus());assertFalse(responseBody(response).contains("9007199254740991"));
    }
    private ReportingFixtures.Fixture fixture(){return new ReportingFixtures(jdbc,new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,manager),new FixtureFactory(sessions,auth)).create();}
    private JsonNode expected(ReportingFixtures.Fixture fixture,String which)throws Exception {
        JsonNode source;try(var in=getClass().getResourceAsStream("/reporting/expected-metrics.json")){source=json.readTree(in).path(which);}
        var value=(ObjectNode)source.deepCopy();LocalDate date=which.equals("D")?fixture.date():fixture.today();value.put("date",date.toString());
        for(var raw:value.path("topDishes")){var item=(ObjectNode)raw;String key=item.remove("dish").asString();item.put("dishId",fixture.dishes().get(key));}
        for(var raw:value.path("inventoryAlerts")){var item=(ObjectNode)raw;String key=item.remove("dish").asString();item.put("dishId",fixture.dishes().get(key));item.put("name","Current menu "+key);}
        for(var raw:value.path("trend")){var item=(ObjectNode)raw;int offset=item.remove("offset").asInt();item.put("date",date.plusDays(offset).toString());}return value;
    }
    @Test void allMetricsUseOneDeliveryDateAndSevenDayZerosAgainstIndependentExpectations()throws Exception {
        var fixture=fixture();var actor=admin();TEST_CLOCK.set(fixture.today().atTime(9,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());
        for(String which:List.of("D","T")) {
            var response=read("/admin/dashboard"+(which.equals("D")?"?date="+fixture.date():""),actor);assertEquals(200,response.getStatus());ContractAssertions.matches("Dashboard",responseBody(response));var actual=(ObjectNode)json.readTree(responseBody(response));assertEquals(TEST_CLOCK.instant().toString(),actual.remove("generatedAt").asString());assertTrue(actual.remove("countingPolicy").asString().contains("Completed Order Amount"));assertEquals(expected(fixture,which),actual);
        }
    }
    @Test void fourStatusesTenTwentyThirtyFortyCountOnlyThirtyCompletedAndExcludeNextDayDelivery()throws Exception {
        var menu=menu();var operations=admin();TEST_CLOCK.set(menu.date().atTime(8,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());var catalog=new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,manager);
        for(int i=1;i<=4;i++) {
            var buyer=browser();String dish=catalog.dish(menu.date(),"Amount dish "+i,"Plain rice with vegetables.",i*1000,"Chinese","Rice",menu.mediaId(),true,true,20);
            var quote=postJson("/checkout/quotes",buyer,Map.of("deliveryDate",menu.date().toString(),"meal",i%2==1?"Lunch":"Dinner","items",List.of(line(dish,1,1))),null);assertEquals(201,quote.getStatus());var order=postJson("/orders",buyer,Map.of("quoteId",field(quote,"quoteId"),"addressText","Reporting fixture office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());String id=field(order,"orderId");
            if(i==2 || i==3){assertEquals(200,write("PATCH","/admin/orders/"+id+"/status",operations,Map.of("status","Confirmed","expectedVersion",1)).getStatus());if(i==3)assertEquals(200,write("PATCH","/admin/orders/"+id+"/status",operations,Map.of("status","Completed","expectedVersion",2)).getStatus());}
            if(i==4)assertEquals(200,postJson("/orders/"+id+"/cancel",buyer,null,null).getStatus());
        }
        var nextMenu=menu();LocalDate next=nextMenu.date();assertEquals(menu.date().plusDays(1),next);var buyer=browser();var quote=postJson("/checkout/quotes",buyer,Map.of("deliveryDate",next.toString(),"meal","Lunch","items",List.of(line(nextMenu.chinese(),1,1))),null);assertEquals(201,quote.getStatus());assertEquals(201,postJson("/orders",buyer,Map.of("quoteId",field(quote,"quoteId"),"addressText","Next-day fixture office"),UUID.randomUUID().toString()).getStatus());
        var response=read("/admin/dashboard?date="+menu.date(),operations);assertEquals(200,response.getStatus());var value=json.readTree(responseBody(response));assertEquals(4,value.path("totalOrders").asLong());assertEquals(3000,value.path("revenueCents").asLong());assertEquals(1,value.path("completedOrders").asLong());assertEquals(2,value.path("pendingOrders").asLong());assertEquals(1,value.path("cancelledOrders").asLong());assertEquals(1,value.path("topDishes").size());assertEquals("Amount dish 3",value.path("topDishes").get(0).path("name").asString());
    }
}
