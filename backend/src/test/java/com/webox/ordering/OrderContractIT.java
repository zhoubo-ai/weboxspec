package com.webox.ordering;
import com.webox.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class OrderContractIT extends OrderingTestSupport {
    @Test void quoteOrderReplayHistoryAndCancellationKeepExactSnapshots() throws Exception {
        var user=browser();var menu=menu();var quote=quote(user,menu,2);
        assertEquals(0,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        assertEquals(0,json.readTree(read("/me/addresses",user).getContentAsString()).size());
        String key=UUID.randomUUID().toString();var body=Map.of("quoteId",quote,"addressText","  上海市 A 楼 3 层 <script>alert(1)</script>  ");
        var created=postJson("/orders",user,body,key);assertEquals(201,created.getStatus());ContractAssertions.matches("Order",created.getContentAsString());
        var order=json.readTree(created.getContentAsString());String id=order.get("orderId").asString();assertEquals("WB-"+id.toUpperCase(Locale.ROOT),order.get("orderNumber").asString());assertEquals(5900,order.get("totalCents").asLong());assertEquals(2,order.get("totalQuantity").asInt());assertEquals("上海市 A 楼 3 层 <script>alert(1)</script>",order.get("addressText").asString());
        var replay=postJson("/orders",user,body,key);assertEquals(200,replay.getStatus());assertEquals("true",replay.getHeader("Idempotency-Replayed"));assertEquals(id,field(replay,"orderId"));
        ContractAssertions.matches("OrderPage",read("/orders?size=100",user).getContentAsString());assertEquals(1,json.readTree(read("/orders?status=Pending",user).getContentAsString()).get("totalElements").asInt());
        jdbc.update("UPDATE dishes SET name='Changed Pasta',base_price_cents=9999,version=version+1 WHERE id=?",menu.pasta());
        var history=json.readTree(read("/orders/"+id,user).getContentAsString());assertEquals("Tomato Pasta",history.get("items").get(0).get("dishName").asString());assertEquals(5900,history.get("totalCents").asLong());
        var addresses=json.readTree(read("/me/addresses",user).getContentAsString());assertEquals(1,addresses.size());ContractAssertions.matches("Address",addresses.get(0).toString());
        var cancelled=postJson("/orders/"+id+"/cancel",user,null,null);assertEquals(200,cancelled.getStatus());assertEquals("Cancelled",field(cancelled,"status"));
        var repeated=postJson("/orders/"+id+"/cancel",user,null,null);assertEquals(cancelled.getContentAsString(),repeated.getContentAsString());
        assertEquals(0,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        assertEquals("Cancelled",field(postJson("/orders",user,body,key),"status"));
    }
    @Test void rejectsOwnerLeaksUnknownFieldsInvalidConfigurationAndChangedPrices() throws Exception {
        var owner=browser();var other=browser();var menu=menu();String quote=quote(owner,menu,1);
        error(postJson("/orders",other,Map.of("quoteId",quote,"addressText","Office"),UUID.randomUUID().toString()),404,"NOT_FOUND");
        for(var extra:List.of("userId","price","status")) {
            var invalid=new HashMap<String,Object>(request(menu,1));invalid.put(extra,"injected");error(postJson("/checkout/quotes",owner,invalid,null),400,"VALIDATION_ERROR");
            var create=new HashMap<String,Object>(Map.of("quoteId",quote,"addressText","Office"));create.put(extra,"injected");error(postJson("/orders",owner,create,UUID.randomUUID().toString()),400,"VALIDATION_ERROR");
        }
        error(postJson("/orders",owner,Map.of("quoteId",quote,"addressText","Office","addressId",UUID.randomUUID().toString()),UUID.randomUUID().toString()),400,"VALIDATION_ERROR");
        error(postJson("/orders",owner,Map.of("quoteId",quote,"addressText","Office"),"not-a-uuid"),400,"VALIDATION_ERROR");
        for(var options:List.of(List.<String>of(),List.of(menu.options().get("Spaghetti"),menu.options().get("Fusilli")),List.of(menu.options().get("Spaghetti"),menu.options().get("Ketchup")))) {
            var invalid=Map.of("deliveryDate",menu.date().toString(),"meal","Lunch","items",List.of(Map.of("dishId",menu.pasta(),"dishVersion",1,"quantity",1,"optionIds",options)));
            error(postJson("/checkout/quotes",owner,invalid,null),400,"VALIDATION_ERROR");
        }
        var order=postJson("/orders",owner,Map.of("quoteId",quote,"addressText","Office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());String id=field(order,"orderId");
        error(read("/orders/"+id,other),404,"NOT_FOUND");error(postJson("/orders/"+id+"/cancel",other,null,null),404,"NOT_FOUND");assertEquals(0,json.readTree(read("/orders",other).getContentAsString()).get("totalElements").asInt());assertEquals(0,json.readTree(read("/me/addresses",other).getContentAsString()).size());
        String addressId=json.readTree(read("/me/addresses",owner).getContentAsString()).get(0).get("addressId").asString();String otherQuote=quote(other,menu,1);
        error(postJson("/orders",other,Map.of("quoteId",otherQuote,"addressId",addressId),UUID.randomUUID().toString()),404,"NOT_FOUND");
        jdbc.update("UPDATE dishes SET base_price_cents=3000,version=version+1 WHERE id=?",menu.pasta());
        var changed=postJson("/checkout/quotes",other,request(menu,1),null);error(changed,409,"DISH_CHANGED");assertEquals(menu.pasta(),json.readTree(changed.getContentAsString()).get("itemIssues").get(0).get("dishId").asString());
    }
    @Test void rejectsEmptyOversizedPastAndMalformedInputsWithoutCreatingOrdersOrAddresses() throws Exception {
        var user=browser();var menu=menu();String quote=quote(user,menu,1);
        for(String address:List.of("  ","🥗".repeat(201))) error(postJson("/orders",user,Map.of("quoteId",quote,"addressText",address),UUID.randomUUID().toString()),400,"VALIDATION_ERROR");
        for(var body:List.of(Map.of("quoteId",quote),Map.of("quoteId",quote,"addressId",UUID.randomUUID().toString(),"addressText","Office"))) error(postJson("/orders",user,body,UUID.randomUUID().toString()),400,"VALIDATION_ERROR");
        var past=new HashMap<String,Object>(request(menu,1));past.put("deliveryDate","2000-01-01");error(postJson("/checkout/quotes",user,past,null),422,"INVALID_DELIVERY_DATE");
        var empty=new HashMap<String,Object>(request(menu,1));empty.put("items",List.of());error(postJson("/checkout/quotes",user,empty,null),400,"VALIDATION_ERROR");
        var six=new HashMap<String,Object>(request(menu,1));six.put("items",List.of(line(menu.pasta(),1,3,menu.options().get("Spaghetti")),line(menu.pasta(),1,3,menu.options().get("Fusilli"))));error(postJson("/checkout/quotes",user,six,null),422,"ORDER_LIMIT_EXCEEDED");
        var fractional=new HashMap<String,Object>(request(menu,1));fractional.put("items",List.of(Map.of("dishId",menu.pasta(),"dishVersion",1,"quantity",1.5,"optionIds",List.of(menu.options().get("Spaghetti")))));error(postJson("/checkout/quotes",user,fractional,null),400,"VALIDATION_ERROR");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));assertEquals(0,json.readTree(read("/me/addresses",user).getContentAsString()).size());
        var valid=postJson("/orders",user,Map.of("quoteId",quote,"addressText","🥗".repeat(200)),UUID.randomUUID().toString());assertEquals(201,valid.getStatus());assertEquals(200,field(valid,"addressText").codePointCount(0,field(valid,"addressText").length()));
    }
    @Test void equalConfigurationsMergeWithSelectedAllergensAndNoStockReservation() throws Exception {
        var user=browser();var menu=menu();var request=Map.of("deliveryDate",menu.date().toString(),"meal","Lunch","items",List.of(line(menu.pasta(),1,1,menu.options().get("Cheese"),menu.options().get("Spaghetti")),line(menu.pasta(),1,1,menu.options().get("Spaghetti"),menu.options().get("Cheese"))));
        var response=postJson("/checkout/quotes",user,request,null);assertEquals(201,response.getStatus());ContractAssertions.matches("Quote",response.getContentAsString());var quote=json.readTree(response.getContentAsString());assertEquals(1,quote.get("items").size());assertEquals(2,quote.get("items").get(0).get("quantity").asInt());assertEquals(5900,quote.get("totalCents").asLong());
        assertEquals(Set.of("Dairy","Gluten"),new HashSet<>(List.of(json.readValue(quote.get("items").get(0).get("effectiveAllergens").toString(),String[].class))));assertEquals(0,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
    }
    @Test void twentyRealOrdersPreserveAllIntegerAmountsAndCanBeReachedThroughPagination() throws Exception {
        var user=browser();var menu=menu();var ids=new HashSet<String>();long verifiedCents=0;var samples=new ArrayList<Map<String,Object>>();
        for(int i=0;i<20;i++) {
            boolean ordinary=i%5==0,burger=i%2==0,extra=i%3!=0,mixed=i%7==0&&!ordinary;int quantity=mixed?2:i%5+1;var selected=new ArrayList<String>();long unit;
            if(ordinary) unit=1800;
            else if(burger) {selected.add(menu.options().get(i%4==0?"Brioche bun":"Plain bun"));selected.add(menu.options().get("Ketchup"));if(extra)selected.add(menu.options().get("Extra cheese"));unit=3200+(i%4==0?200:0)+(extra?300:0);}
            else {selected.add(menu.options().get(i%3==0?"Fusilli":"Spaghetti"));if(extra)selected.add(menu.options().get("Cheese"));unit=2650+(extra?300:0);}
            if(!ordinary && extra && i%4!=0) {selected.add(menu.options().get(burger?"Extra tomato":"Extra basil"));unit+=100;}
            String dish=ordinary?menu.chinese():burger?menu.burger():menu.pasta();var expectedLines=new ArrayList<Map<String,Object>>();expectedLines.add(line(dish,1,quantity,selected.toArray(String[]::new)));if(mixed)expectedLines.add(line(menu.chinese(),1,1));long expected=unit*quantity+(mixed?1800:0);
            var body=Map.of("deliveryDate",menu.date().toString(),"meal","Lunch","items",expectedLines);
            var quote=postJson("/checkout/quotes",user,body,null);assertEquals(201,quote.getStatus());long quoteTotal=json.readTree(quote.getContentAsString()).get("totalCents").asLong();assertEquals(expected,quoteTotal);
            var order=postJson("/orders",user,Map.of("quoteId",field(quote,"quoteId"),"addressText","Office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());ContractAssertions.matches("Order",order.getContentAsString());String id=field(order,"orderId");ids.add(id);var orderJson=json.readTree(order.getContentAsString());
            long actual=orderJson.get("totalCents").asLong(),stored=jdbc.queryForObject("SELECT SUM(subtotal_cents) FROM order_items WHERE order_id=?",Long.class,id);assertEquals(expected,actual);assertEquals(expected,stored);verifiedCents+=expected;
            var details=new ArrayList<Map<String,Object>>();
            for(var actualLine:orderJson.get("items")) {
                var submitted=expectedLines.stream().filter(item->item.get("dishId").equals(actualLine.get("dishId").asString())).findFirst().orElseThrow();assertEquals(submitted.get("quantity"),actualLine.get("quantity").asInt());
                var actualOptions=new HashSet<String>();var optionNames=new ArrayList<String>();for(var option:actualLine.get("selectedOptions")) {String optionId=option.get("optionId").asString(),name=option.get("optionName").asString();actualOptions.add(optionId);optionNames.add(name);assertEquals(menu.options().get(name),optionId);}
                assertEquals(new HashSet<>((List<?>)submitted.get("optionIds")),actualOptions);
                details.add(Map.of("dish",actualLine.get("dishName").asString(),"options",optionNames,"quantity",actualLine.get("quantity").asInt(),"unitCents",actualLine.get("unitPriceCents").asLong(),"subtotalCents",actualLine.get("subtotalCents").asLong()));
            }
            samples.add(Map.of("sample",i+1,"items",details,"expectedCents",expected,"quoteCents",quoteTotal,"orderCents",actual,"databaseItemSumCents",stored,"configurationMatches",true));
            if(i<19) assertEquals(200,postJson("/orders/"+id+"/cancel",user,null,null).getStatus());
        }
        var listed=new HashSet<String>();for(int page=0;page<3;page++) {var response=read("/orders?size=7&page="+page,user);assertEquals(200,response.getStatus());ContractAssertions.matches("OrderPage",response.getContentAsString());var jsonPage=json.readTree(response.getContentAsString());assertEquals(20,jsonPage.get("totalElements").asInt());jsonPage.get("items").forEach(item->listed.add(item.get("orderId").asString()));}
        assertEquals(ids,listed);assertEquals(verifiedCents,jdbc.queryForObject("SELECT SUM(total_cents) FROM orders WHERE user_id=?",Long.class,user.identity().user().userId()));assertEquals(19,json.readTree(read("/orders?status=Cancelled",user).getContentAsString()).get("totalElements").asInt());
        var directory=java.nio.file.Path.of("target/validation");java.nio.file.Files.createDirectories(directory);java.nio.file.Files.writeString(directory.resolve("order-amounts.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(samples));
    }
}
