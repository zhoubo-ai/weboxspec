package com.webox.ordering;
import com.webox.support.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class OrderTransactionIT extends OrderingTestSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.webox.inventory.InventoryMutationService inventory;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean QuoteRepository quotes;
    @org.springframework.beans.factory.annotation.Autowired OrderStatusService statuses;
    @Test void twentyIdenticalRequestsCreateOneOrderAndOneInventoryEvent() throws Exception {
        var user=browser();var menu=menu();String quote=quote(user,menu,2),key=UUID.randomUUID().toString();var body=Map.of("quoteId",quote,"addressText","Office A");
        var responses=concurrent(20,i->postJson("/orders",user,body,key));assertEquals(1,responses.stream().filter(r->r.getStatus()==201).count());assertEquals(19,responses.stream().filter(r->r.getStatus()==200).count());
        assertEquals(1,responses.stream().map(r->field(r,"orderId")).distinct().count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));
        assertEquals(2,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,menu.date()+"/"+menu.pasta()));
        error(postJson("/orders",user,Map.of("quoteId",quote,"addressText","Office B"),key),409,"IDEMPOTENCY_KEY_REUSED");
        error(postJson("/orders",user,body,UUID.randomUUID().toString()),409,"QUOTE_CONSUMED");
    }
    @Test void twentyPeopleCompeteForTenPortionsWithoutOverselling() throws Exception {
        var menu=menu();jdbc.update("UPDATE daily_supply SET total_quantity=10 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.pasta());
        var people=new ArrayList<Browser>();var quotes=new ArrayList<String>();for(int i=0;i<20;i++) {var person=browser();people.add(person);quotes.add(quote(person,menu,1));}
        var responses=concurrent(20,i->postJson("/orders",people.get(i),Map.of("quoteId",quotes.get(i),"addressText","Office"),UUID.randomUUID().toString()));
        assertEquals(10,responses.stream().filter(r->r.getStatus()==201).count());for(var response:responses) if(response.getStatus()!=201) error(response,409,"INSUFFICIENT_STOCK");
        assertEquals(10,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE delivery_date=?",Integer.class,menu.date()));
    }
    @Test void aLaterStockFailureRollsBackAllDishesAddressesAndEvents() throws Exception {
        var user=browser();var menu=menu();var request=Map.of("deliveryDate",menu.date().toString(),"meal","Lunch","items",List.of(line(menu.pasta(),1,1,menu.options().get("Spaghetti")),line(menu.chinese(),1,1)));
        var quoteResponse=postJson("/checkout/quotes",user,request,null);assertEquals(201,quoteResponse.getStatus());
        jdbc.update("UPDATE daily_supply SET total_quantity=0 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.chinese());
        error(postJson("/orders",user,Map.of("quoteId",field(quoteResponse,"quoteId"),"addressText","Not saved"),UUID.randomUUID().toString()),409,"INSUFFICIENT_STOCK");
        assertEquals(0,jdbc.queryForObject("SELECT SUM(allocated_quantity) FROM daily_supply WHERE delivery_date=?",Integer.class,menu.date()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));assertEquals(0,json.readTree(read("/me/addresses",user).getContentAsString()).size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key LIKE ?",Integer.class,menu.date()+"/%"));
    }
    @Test void twoPeopleCompeteForOnePortionAndTheLoserReceivesTheExactAvailability() throws Exception {
        var menu=menu();jdbc.update("UPDATE daily_supply SET total_quantity=1 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.pasta());
        var people=List.of(browser(),browser());var prepared=List.of(quote(people.get(0),menu,1),quote(people.get(1),menu,1));
        var responses=concurrent(2,i->postJson("/orders",people.get(i),Map.of("quoteId",prepared.get(i),"addressText","One portion office"),UUID.randomUUID().toString()));
        assertEquals(1,responses.stream().filter(r->r.getStatus()==201).count());var loser=responses.stream().filter(r->r.getStatus()!=201).findFirst().orElseThrow();error(loser,409,"INSUFFICIENT_STOCK");var body=json.readTree(responseBody(loser)).path("itemIssues").get(0);assertEquals(menu.pasta(),body.path("dishId").asString());assertEquals(0,body.path("availableQuantity").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE delivery_date=?",Integer.class,menu.date()));
    }
    @Test void differentKeysCannotReuseAQuoteOrCreateTwoActiveOrdersForTheSameMeal() throws Exception {
        var user=browser();var menu=menu();String first=quote(user,menu,1),second=quote(user,menu,1);
        var responses=concurrent(2,i->postJson("/orders",user,Map.of("quoteId",i==0?first:second,"addressText","Office"),UUID.randomUUID().toString()));
        assertEquals(1,responses.stream().filter(r->r.getStatus()==201).count());for(var response:responses) if(response.getStatus()!=201) error(response,409,"ACTIVE_ORDER_EXISTS");
        var another=browser();String shared=quote(another,menu,1);
        var duplicates=concurrent(2,i->postJson("/orders",another,Map.of("quoteId",shared,"addressText","Office"),UUID.randomUUID().toString()));
        assertEquals(1,duplicates.stream().filter(r->r.getStatus()==201).count());for(var response:duplicates) if(response.getStatus()!=201) error(response,409,"QUOTE_CONSUMED");
    }
    @Test void concurrentCancellationRestoresOnceAndReplaySurvivesQuoteCleanupAndExpiry() throws Exception {
        var user=browser();var menu=menu();String quote=quote(user,menu,2),key=UUID.randomUUID().toString();var body=Map.of("quoteId",quote,"addressText","Office");
        var created=postJson("/orders",user,body,key);assertEquals(201,created.getStatus());String id=field(created,"orderId");
        var cancelled=concurrent(20,i->postJson("/orders/"+id+"/cancel",user,null,null));for(var response:cancelled) { assertEquals(200,response.getStatus());assertEquals("Cancelled",field(response,"status")); }
        assertEquals(0,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        assertEquals(2,jdbc.queryForObject("SELECT version FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,menu.date()+"/"+menu.pasta()));
        jdbc.update("DELETE FROM checkout_quote_items WHERE quote_id=?",quote);jdbc.update("DELETE FROM checkout_quotes WHERE id=?",quote);TEST_CLOCK.set(java.time.Instant.parse("2035-01-01T00:00:00Z"));
        var replay=postJson("/orders",user,Map.of("addressText","  Office  ","quoteId",quote.toUpperCase(Locale.ROOT)),key.toUpperCase(Locale.ROOT));assertEquals(200,replay.getStatus());assertEquals(id,field(replay,"orderId"));assertEquals("Cancelled",field(replay,"status"));
        error(postJson("/orders",user,body,UUID.randomUUID().toString()),409,"QUOTE_CONSUMED");
    }
    @Test void completionKeepsAllocatedStockAndOldKeyWhileReleasingTheActiveMeal() throws Exception {
        var user=browser();var menu=menu();String quote=quote(user,menu,1),key=UUID.randomUUID().toString();var body=Map.of("quoteId",quote,"addressText","Office");
        var created=postJson("/orders",user,body,key);assertEquals(201,created.getStatus());String id=field(created,"orderId");
        var admin=new com.webox.auth.UserIdentity(menu.adminId(),"fixture@example.com",com.webox.shared.DomainTypes.Role.Admin);
        statuses.administer(admin,id,com.webox.shared.DomainTypes.OrderStatus.Confirmed,1);statuses.administer(admin,id,com.webox.shared.DomainTypes.OrderStatus.Completed,2);
        assertEquals(1,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));
        assertEquals("Completed",field(postJson("/orders",user,body,key),"status"));error(postJson("/orders/"+id+"/cancel",user,null,null),409,"ORDER_NOT_CANCELLABLE");
        String next=quote(user,menu,1);assertEquals(201,postJson("/orders",user,Map.of("quoteId",next,"addressText","Office"),UUID.randomUUID().toString()).getStatus());
        assertEquals(1,json.readTree(read("/me/addresses",user).getContentAsString()).size());
    }
    @Test void failureAfterAddressAndStockWritesRollsBackAndSameKeyCanThenSucceed() throws Exception {
        var user=browser();var menu=menu();String quote=quote(user,menu,1),key=UUID.randomUUID().toString();var body=Map.of("quoteId",quote,"addressText","Not yet saved");
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("Injected transaction failure")).when(quotes).consume(org.mockito.ArgumentMatchers.eq(quote),org.mockito.ArgumentMatchers.anyString());
        try { error(postJson("/orders",user,body,key),500,"INTERNAL_ERROR"); } finally { org.mockito.Mockito.reset(quotes); }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));assertEquals(0,json.readTree(read("/me/addresses",user).getContentAsString()).size());
        assertEquals(0,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,menu.date()+"/"+menu.pasta()));
        assertNull(jdbc.queryForObject("SELECT consumed_order_id FROM checkout_quotes WHERE id=?",String.class,quote));assertEquals(201,postJson("/orders",user,body,key).getStatus());
    }
    @Test void expiryAndCutoffAreRecheckedAfterARealBlockedDishLock() throws Exception {
        for(boolean quoteExpiry:List.of(true,false)) {
            var user=browser();var menu=menu();var today=java.time.LocalDate.of(2026,10,16);
            jdbc.update("INSERT IGNORE INTO daily_menus(delivery_date,revision,updated_by,updated_at) VALUES (?,?,?,?)",today,1,menu.adminId(),java.sql.Timestamp.from(INITIAL_TIME));
            jdbc.update("INSERT INTO daily_supply SELECT ?,dish_id,scheduled,total_quantity,allocated_quantity,version,updated_at FROM daily_supply WHERE delivery_date=?",today,menu.date());
            TEST_CLOCK.set(quoteExpiry?INITIAL_TIME:java.time.Instant.parse("2026-10-16T01:59:59Z"));
            var request=new HashMap<String,Object>(request(menu,1));request.put("deliveryDate",today.toString());var response=postJson("/checkout/quotes",user,request,null);assertEquals(201,response.getStatus());String quote=field(response,"quoteId");
            var arrived=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();var pending=new java.util.concurrent.atomic.AtomicReference<Future<MockHttpServletResponse>>();
            org.mockito.Mockito.doAnswer(invocation->{arrived.countDown();return invocation.callRealMethod();}).when(inventory).lock(org.mockito.ArgumentMatchers.eq(today),org.mockito.ArgumentMatchers.anyList());
            try {
                new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status->{
                    jdbc.queryForObject("SELECT version FROM dishes WHERE id=? FOR UPDATE",Long.class,menu.pasta());
                    pending.set(executor.submit(()->postJson("/orders",user,Map.of("quoteId",quote,"addressText","Office"),UUID.randomUUID().toString())));
                    try { assertTrue(arrived.await(10,TimeUnit.SECONDS)); } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);}
                    TEST_CLOCK.set(java.time.Instant.parse(quoteExpiry?"2026-10-16T01:05:00Z":"2026-10-16T02:00:00Z"));
                });
                error(pending.get().get(10,TimeUnit.SECONDS),409,quoteExpiry?"QUOTE_EXPIRED":"SLOT_EXPIRED");assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));
            } finally {executor.shutdownNow();org.mockito.Mockito.reset(inventory);TEST_CLOCK.set(INITIAL_TIME);}
        }
    }
    @Test void transientLockFailureRetriesTheWholeTransactionAndStopsAfterThreeAttempts() throws Exception {
        for(boolean persistent:List.of(false,true)) {
            var user=browser();var menu=menu();String quote=quote(user,menu,1);var attempts=new java.util.concurrent.atomic.AtomicInteger();
            org.mockito.Mockito.doAnswer(invocation->{if(persistent || attempts.getAndIncrement()==0) {if(persistent)attempts.incrementAndGet();throw new org.springframework.dao.CannotAcquireLockException("Injected lock timeout after writes");}return invocation.callRealMethod();}).when(quotes).consume(org.mockito.ArgumentMatchers.eq(quote),org.mockito.ArgumentMatchers.anyString());
            try {
                var response=postJson("/orders",user,Map.of("quoteId",quote,"addressText","Office"),UUID.randomUUID().toString());
                if(persistent) {error(response,503,"TEMPORARY_UNAVAILABLE");assertEquals(3,attempts.get());assertTrue(json.readTree(response.getContentAsString()).get("retryable").asBoolean());}
                else {assertEquals(201,response.getStatus());assertEquals(2,attempts.get());}
                int expected=persistent?0:1;assertEquals(expected,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,user.identity().user().userId()));assertEquals(expected,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));assertEquals(expected,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,menu.date()+"/"+menu.pasta()));
            } finally { org.mockito.Mockito.reset(quotes); }
        }
    }
    @FunctionalInterface interface Request { MockHttpServletResponse run(int index) throws Exception; }
    static List<MockHttpServletResponse> concurrent(int count,Request request) throws Exception {
        var pool=Executors.newFixedThreadPool(count);var ready=new CountDownLatch(count);var go=new CountDownLatch(1);var futures=new ArrayList<Future<MockHttpServletResponse>>();
        try {
            for(int i=0;i<count;i++) {final int index=i;futures.add(pool.submit(()->{ready.countDown();if(!go.await(10,TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out");return request.run(index);}));}
            assertTrue(ready.await(10,TimeUnit.SECONDS));go.countDown();var results=new ArrayList<MockHttpServletResponse>();for(var future:futures) results.add(future.get(30,TimeUnit.SECONDS));return results;
        } finally { go.countDown();pool.shutdownNow(); }
    }
}
