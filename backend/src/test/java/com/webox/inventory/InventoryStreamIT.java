package com.webox.inventory;

import com.webox.support.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"webox.inventory.dispatch-enabled=true","webox.inventory.heartbeat-ms=100","webox.inventory.max-duration-ms=3000"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class InventoryStreamIT extends AdminTestSupport {
    @org.springframework.beans.factory.annotation.Autowired InventorySubscriptions subscriptions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean InventoryQuery inventory;
    @LocalServerPort int port;
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).version(HttpClient.Version.HTTP_1_1).build();
    private record Event(String name,String data) {}
    private class Stream implements AutoCloseable {
        final HttpResponse<InputStream> response;final BlockingQueue<Event> events=new LinkedBlockingQueue<>();final Thread reader;
        Stream(LocalDate date,Browser user) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/menus/"+date+"/events")).header("Cookie","WEBOXSESSION="+user.identity().sessionId()).header("Accept","text/event-stream").timeout(Duration.ofSeconds(5)).GET().build();
            response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse(""));
            reader=new Thread(()->{try(var input=new BufferedReader(new InputStreamReader(response.body(),java.nio.charset.StandardCharsets.UTF_8))){String name="",data="",line;while((line=input.readLine())!=null){if(line.startsWith("event:"))name=line.substring(6).strip();else if(line.startsWith("data:"))data=line.substring(5).strip();else if(line.isEmpty() && !name.isEmpty()){events.offer(new Event(name,data));name="";data="";}}}catch(IOException ignored){}finally{events.offer(new Event("EOF",""));}},"inventory-test-reader");reader.setDaemon(true);reader.start();
        }
        Event next(String name) throws Exception {long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<deadline){var event=events.poll(100,TimeUnit.MILLISECONDS);if(event==null)continue;if(event.name().equals(name))return event;assertNotEquals("EOF",event.name(),"Stream ended before "+name);}throw new AssertionError("Missing SSE event: "+name);}
        @Override public void close() throws Exception {response.body().close();reader.join(1000);}
    }
    @Test void completeSnapshotUsesVisibleMembershipAndRequiresAuthentication() throws Exception {
        var user=browser();var menu=menu();var response=read("/menus/"+menu.date()+"/inventory",user);assertEquals(200,response.getStatus());ContractAssertions.matches("InventorySnapshot",responseBody(response));var tree=json.readTree(responseBody(response));assertEquals(5,tree.path("items").size());assertEquals(1,tree.path("menuRevision").asLong());assertFalse(responseBody(response).contains(menu.hidden()));assertFalse(responseBody(response).contains(menu.unscheduled()));assertFalse(responseBody(response).contains("email"));
        error(read("/menus/2026-02-30/inventory",user),400,"VALIDATION_ERROR");assertEquals(401,mvc.perform(get("/api/v1/menus/"+menu.date()+"/inventory")).andReturn().getResponse().getStatus());
    }
    @Test void snapshotPrecedesCommittedAbsoluteOrderAndCancellationUpdates() throws Exception {
        var user=browser();var menu=menu();try(var stream=new Stream(menu.date(),user)) {
            var first=stream.next("inventory.snapshot");ContractAssertions.matches("InventorySnapshot",first.data());
            var order=postJson("/orders",user,Map.of("quoteId",quote(user,menu,1),"addressText","Stream office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());var update=stream.next("inventory.updated");ContractAssertions.matches("InventoryUpdated",update.data());var body=json.readTree(update.data());assertEquals(menu.pasta(),body.path("dishId").asString());assertEquals(19,body.path("remaining").asInt());assertEquals(1,body.path("version").asLong());assertEquals(TEST_CLOCK.instant().toString(),body.path("occurredAt").asString());assertFalse(update.data().contains("Stream office"));
            assertEquals(200,postJson("/orders/"+field(order,"orderId")+"/cancel",user,null,null).getStatus());var restored=json.readTree(stream.next("inventory.updated").data());assertEquals(20,restored.path("remaining").asInt());assertEquals(2,restored.path("version").asLong());
        }
    }
    @Test void emptyDateReceivesMenuChangesAndReconnectAlwaysBeginsWithANewSnapshot() throws Exception {
        var user=browser();var admin=admin();var menu=menu();LocalDate date=menu.date().plusYears(5);
        try(var stream=new Stream(date,user)) {
            assertEquals(0,json.readTree(stream.next("inventory.snapshot").data()).path("items").size());assertEquals(200,write("PUT","/admin/menus/"+date,admin,Map.of("expectedRevision",0,"items",List.of(Map.of("dishId",menu.pasta(),"totalQuantity",3)))).getStatus());ContractAssertions.matches("MenuChanged",stream.next("menu.changed").data());
        }
        try(var stream=new Stream(date,user)) {var snapshot=json.readTree(stream.next("inventory.snapshot").data());assertEquals(1,snapshot.path("menuRevision").asInt());assertEquals(1,snapshot.path("items").size());assertEquals(3,snapshot.path("items").get(0).path("remaining").asInt());}
    }
    @Test void sessionRevocationTerminatesTheStreamWithoutExtendingItsIdleTime() throws Exception {
        var user=browser();var menu=menu();try(var stream=new Stream(menu.date(),user)) {
            stream.next("inventory.snapshot");long accessed=jdbc.queryForObject("SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID=?",Long.class,user.identity().sessionId());Thread.sleep(250);assertEquals(accessed,jdbc.queryForObject("SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID=?",Long.class,user.identity().sessionId()));sessions.deleteById(user.identity().sessionId());assertEquals("Your session has expired. Please sign in again.",json.readTree(stream.next("session.expired").data()).path("message").asString());stream.next("EOF");
        }
    }
    @Test void aBurstPreservesEveryFrameAndTheNextWriteRechecksRevocation() throws Exception {
        var user=browser();var menu=menu();try(var stream=new Stream(menu.date(),user)) {
            stream.next("inventory.snapshot");
            for(int revision=1;revision<=100;revision++)subscriptions.publish(menu.date(),"menu.changed",json.writeValueAsString(Map.of("deliveryDate",menu.date(),"menuRevision",revision)));
            for(int revision=1;revision<=100;revision++)assertEquals(revision,json.readTree(stream.next("menu.changed").data()).path("menuRevision").asInt());
            sessions.deleteById(user.identity().sessionId());
            subscriptions.publish(menu.date(),"menu.changed",json.writeValueAsString(Map.of("deliveryDate",menu.date(),"menuRevision",101)));
            stream.next("session.expired");stream.next("EOF");
        }
    }
    @Test void lifetimeAndPerSessionCapacityAreBounded() throws Exception {
        var user=browser();var menu=menu();try(var a=new Stream(menu.date(),user);var b=new Stream(menu.date(),user);var c=new Stream(menu.date(),user)) {
            a.next("inventory.snapshot");b.next("inventory.snapshot");c.next("inventory.snapshot");var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/menus/"+menu.date()+"/events")).header("Cookie","WEBOXSESSION="+user.identity().sessionId()).header("Accept","text/event-stream").timeout(Duration.ofSeconds(2)).GET().build();var response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());assertEquals(429,response.statusCode());ContractAssertions.matches("Error",response.body());a.next("EOF");
        }
    }
    @Test void registrationBuffersACommitThatOccursBeforeTheSnapshotRead() throws Exception {
        var user=browser();var menu=menu();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        org.mockito.Mockito.doAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return call.callRealMethod();}).when(inventory).snapshot(menu.date());
        Stream stream=null;
        try {
            var opening=executor.submit(()->new Stream(menu.date(),user));assertTrue(entered.await(3,TimeUnit.SECONDS));var order=postJson("/orders",user,Map.of("quoteId",quote(user,menu,1),"addressText","Buffered office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=? AND published_at IS NOT NULL",Integer.class,menu.date()+"/"+menu.pasta())==0 && System.nanoTime()<deadline)Thread.sleep(10);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=? AND published_at IS NOT NULL",Integer.class,menu.date()+"/"+menu.pasta()));release.countDown();stream=opening.get(3,TimeUnit.SECONDS);var snapshot=json.readTree(stream.next("inventory.snapshot").data());boolean found=false;for(var row:snapshot.path("items"))if(row.path("dishId").asString().equals(menu.pasta())){assertEquals(19,row.path("remaining").asInt());found=true;}assertTrue(found);assertEquals(19,json.readTree(stream.next("inventory.updated").data()).path("remaining").asInt());
        }finally{release.countDown();if(stream!=null)stream.close();executor.shutdownNow();}
    }
}
