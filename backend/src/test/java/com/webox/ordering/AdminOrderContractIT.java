package com.webox.ordering;
import com.webox.support.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AdminOrderContractIT extends AdminTestSupport {
    private String placed(Browser user,CatalogFixtures.Menu menu) throws Exception {var result=postJson("/orders",user,Map.of("quoteId",quote(user,menu,1),"addressText","上海市 A 楼 3 层"),UUID.randomUUID().toString());assertEquals(201,result.getStatus());return field(result,"orderId");}
    @Test void administratorCanFilterReadConfirmAndCompleteWhileRetainingSnapshotsAndStock() throws Exception {
        var admin=admin();var user=browser();var menu=menu();String id=placed(user,menu);var response=read("/admin/orders/"+id,admin);assertEquals(200,response.getStatus());ContractAssertions.matches("AdminOrder",responseBody(response));assertEquals(user.identity().user().userId(),json.readTree(responseBody(response)).path("customer").path("userId").asString());
        var list=read("/admin/orders?deliveryDate="+menu.date()+"&meal=Lunch&status=Pending&size=1",admin);assertEquals(200,list.getStatus());ContractAssertions.matches("AdminOrderPage",responseBody(list));assertEquals(id,json.readTree(responseBody(list)).path("items").get(0).path("orderId").asString());
        error(write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Completed","expectedVersion",1)),409,"INVALID_ORDER_TRANSITION");
        var confirmed=write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Confirmed","expectedVersion",1));assertEquals(200,confirmed.getStatus());ContractAssertions.matches("AdminOrder",responseBody(confirmed));assertEquals("Confirmed",field(read("/orders/"+id,user),"status"));
        error(write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Cancelled","expectedVersion",1)),409,"VERSION_CONFLICT");error(write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Cancelled","expectedVersion",2)),409,"INVALID_ORDER_TRANSITION");
        var completed=write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Completed","expectedVersion",2));assertEquals(200,completed.getStatus());assertEquals("Completed",field(completed,"status"));assertEquals(json.readTree(responseBody(response)).path("items"),json.readTree(responseBody(completed)).path("items"));assertEquals(1,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        error(write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Cancelled","expectedVersion",3)),409,"INVALID_ORDER_TRANSITION");assertEquals(201,postJson("/checkout/quotes",user,request(menu,1),null).getStatus());
    }
    @Test void repeatedAdminCancellationIgnoresStaleVersionWithoutRepeatingSideEffects() throws Exception {
        var admin=admin();var user=browser();var menu=menu();String id=placed(user,menu);var cancelled=write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Cancelled","expectedVersion",1));assertEquals(200,cancelled.getStatus());ContractAssertions.matches("AdminOrder",responseBody(cancelled));long events=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Long.class,menu.date()+"/"+menu.pasta());
        var repeated=write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Cancelled","expectedVersion",0));assertEquals(200,repeated.getStatus());assertEquals(json.readTree(responseBody(cancelled)),json.readTree(responseBody(repeated)));assertEquals(events,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Long.class,menu.date()+"/"+menu.pasta()));assertEquals(0,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        error(write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status","Confirmed","expectedVersion",2)),409,"INVALID_ORDER_TRANSITION");
    }
    @Test void confirmationAndCancellationCompeteForOneLegalTransition() throws Exception {
        var admin=admin();var user=browser();var menu=menu();String id=placed(user,menu);assertEquals(200,read("/admin/orders/"+id,admin).getStatus());var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var futures=new ArrayList<Future<Integer>>();for(String status:List.of("Confirmed","Cancelled")) futures.add(executor.submit(()->{gate.await();return write("PATCH","/admin/orders/"+id+"/status",admin,Map.of("status",status,"expectedVersion",1)).getStatus();}));gate.countDown();var statuses=new ArrayList<Integer>();for(var job:futures)statuses.add(job.get(10,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);String state=field(read("/orders/"+id,user),"status");assertEquals(state.equals("Cancelled")?0:1,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));}finally{executor.shutdownNow();}
    }
    @Test void staffCannotReadOrChangeOthersOrdersThroughAdminAndCsrfIsRequired() throws Exception {
        var admin=admin();var user=browser();var other=browser();var menu=menu();String id=placed(user,menu);assertEquals(200,read("/admin/orders",admin).getStatus());
        for(String path:List.of("/admin/orders","/admin/orders/"+id))error(read(path,other),403,"FORBIDDEN");error(write("PATCH","/admin/orders/"+id+"/status",other,Map.of("status","Cancelled","expectedVersion",1)),403,"FORBIDDEN");
        assertEquals(403,mvc.perform(patch("/api/v1/admin/orders/"+id+"/status").cookie(admin.identity().cookie()).contentType("application/json").content("{\"status\":\"Cancelled\",\"expectedVersion\":1}")).andReturn().getResponse().getStatus());
        for(var body:List.of(Map.of("status","Pending","expectedVersion",1),Map.of("status","Confirmed","expectedVersion",-1),Map.of("status","Confirmed"),Map.of("status","Confirmed","expectedVersion",1,"userId",user.identity().user().userId()))) error(write("PATCH","/admin/orders/"+id+"/status",admin,body),400,"VALIDATION_ERROR");assertEquals("Pending",field(read("/orders/"+id,user),"status"));
    }
}
