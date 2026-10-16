package com.webox.recommendation;
import com.sun.net.httpserver.*;
import com.webox.auth.*;
import com.webox.shared.DomainTypes.*;
import com.webox.support.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled provider failures only; real recommendation acceptance uses the separate llm-live suite. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"OPENROUTER_API_KEY=test-fault-provider","webox.openrouter.allow-loopback=true","AI_TOTAL_TIMEOUT_MS=5000"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationFlowIT extends AdminTestSupport {
    private static final JsonMapper JSON=new JsonMapper();private static final AtomicReference<Scenario> scenario=new AtomicReference<>();private static final HttpServer upstream=server();
    private static final class Scenario {final String mode;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);final AtomicInteger requests=new AtomicInteger();volatile String dish;Scenario(String mode,String dish){this.mode=mode;this.dish=dish;}}
    private static HttpServer server(){try{var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newCachedThreadPool(r->{var t=new Thread(r,"provider-fault");t.setDaemon(true);return t;}));server.createContext("/chat/completions",RecommendationFlowIT::provider);server.start();return server;}catch(IOException failure){throw new ExceptionInInitializerError(failure);}}
    @DynamicPropertySource static void providerProperties(DynamicPropertyRegistry properties){properties.add("OPENROUTER_BASE_URL",()->"http://127.0.0.1:"+upstream.getAddress().getPort());}
    @AfterAll static void stopProvider(){upstream.stop(0);}
    @LocalServerPort int port;@Autowired RecommendationService service;@Autowired PreferencesService preferences;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static void provider(HttpExchange exchange)throws IOException {
        var current=scenario.get();current.requests.incrementAndGet();var request=JSON.readTree(exchange.getRequestBody().readAllBytes());
        try {
            if(current.mode.equals("http-error")){exchange.sendResponseHeaders(429,0);exchange.getResponseBody().write("private provider secret".getBytes(StandardCharsets.UTF_8));return;}
            if(!request.path("stream").asBoolean()) {
                if(current.mode.equals("blocked-intent")){current.entered.countDown();current.release.await(10,TimeUnit.SECONDS);}
                var intent=new IntentService.Extracted("DEFAULT",null,null,null,false,List.of(),null,null,null,null,List.of(),null);
                var response=Map.of("id","fault-intent","model","fault-model","provider","Fault","choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",JSON.writeValueAsString(intent)))));
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(JSON.writeValueAsBytes(response));return;
            }
            exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,0);
            if(current.mode.equals("change")){current.entered.countDown();current.release.await(10,TimeUnit.SECONDS);}
            String inner=JSON.writeValueAsString(Map.of("recommendations",List.of(Map.of("dishId",current.dish,"reason","A choice from the available menu."))));
            String content=current.mode.equals("truncate")?inner.substring(0,inner.length()-2):inner;
            String chunk="data: "+JSON.writeValueAsString(Map.of("id","fault-generation","model","fault-model","provider","Fault","choices",List.of(Map.of("index",0,"delta",Map.of("content",content),"finish_reason","stop"))))+"\n\n";
            exchange.getResponseBody().write(chunk.getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();
            if(!current.mode.equals("truncate"))exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}finally{exchange.close();}
    }
    private HttpRequest request(Browser user,LocalDate date){return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/recommendations/stream")).header("Cookie","WEBOXSESSION="+user.identity().sessionId()).header("X-XSRF-TOKEN",user.csrf()).header("Accept","text/event-stream").header("Content-Type","application/json").timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("deliveryDate",date.toString(),"meal","Lunch","prompt","A lunch, please.")))).build();}
    private record Event(String name,tools.jackson.databind.JsonNode body){}
    private List<Event> events(String body){var result=new ArrayList<Event>();for(String block:body.split("\n\n"))if(block.startsWith("event: ")){int data=block.indexOf("\ndata: ");result.add(new Event(block.substring(7,data),json.readTree(block.substring(data+7))));}return result;}
    private void drained()throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(service.inFlight()>0 && System.nanoTime()<until)Thread.sleep(10);assertEquals(0,service.inFlight());}
    @AfterEach void cleanup()throws Exception{var current=scenario.get();if(current!=null)current.release.countDown();drained();}
    @Test void completeValidatedCardHasOneMetaOneDoneAndDatabaseDishData()throws Exception {
        var user=browser();var menu=menu();var fault=new Scenario("complete",menu.pasta());scenario.set(fault);
        var response=http.send(request(user,menu.date()),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());var events=events(response.body());assertEquals(List.of("meta","card","done"),events.stream().map(Event::name).toList());ContractAssertions.matches("RecommendationCard",events.get(1).body().toString());assertEquals("Tomato Pasta",events.get(1).body().path("dish").path("name").asString());assertEquals("COMPLETE",events.get(2).body().path("outcome").asString());assertEquals(1,events.get(2).body().path("count").asInt());assertEquals(2,fault.requests.get());
    }
    @Test void truncatedJsonAfterOneItemIsPartialErrorAndProviderHttpErrorsStayPrivate()throws Exception {
        var user=browser();var menu=menu();scenario.set(new Scenario("truncate",menu.pasta()));var truncated=events(http.send(request(user,menu.date()),HttpResponse.BodyHandlers.ofString()).body());assertEquals(List.of("meta","card","error"),truncated.stream().map(Event::name).toList());assertEquals("AI_INVALID_RESPONSE",truncated.get(2).body().path("code").asString());assertTrue(truncated.get(2).body().path("partial").asBoolean());drained();
        scenario.set(new Scenario("http-error",menu.pasta()));var response=http.send(request(user,menu.date()),HttpResponse.BodyHandlers.ofString());var failed=events(response.body());assertEquals(List.of("meta","error"),failed.stream().map(Event::name).toList());assertEquals("AI_UNAVAILABLE",failed.get(1).body().path("code").asString());assertFalse(response.body().contains("private provider"));assertEquals(200,read("/menus/"+menu.date(),user).getStatus());
    }
    @Test void preferenceAndStockChangesBeforeTheFirstItemCannotLeakAnIneligibleCard()throws Exception {
        for(boolean allergy:List.of(true,false)) {
            var user=browser();var menu=menu();var fault=new Scenario("change",menu.pasta());scenario.set(fault);var result=http.sendAsync(request(user,menu.date()),HttpResponse.BodyHandlers.ofString());assertTrue(fault.entered.await(3,TimeUnit.SECONDS));
            if(allergy)preferences.replace(user.identity().user().userId(),new PreferencesModels.Write(List.of(Allergen.Dairy),List.of(),null,null,null,null,0L));else jdbc.update("UPDATE daily_supply SET total_quantity=0,version=version+1 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.pasta());
            fault.release.countDown();var events=events(result.get(3,TimeUnit.SECONDS).body());assertEquals(List.of("meta","done"),events.stream().map(Event::name).toList());assertEquals("NO_MATCH",events.get(1).body().path("outcome").asString());drained();
        }
    }
    @Test void totalDeadlineIncludesTheQueueAndTheTenthRequestIsRejectedBeforeHeaders()throws Exception {
        var user=browser();var menu=menu();var fault=new Scenario("blocked-intent",menu.pasta());scenario.set(fault);var results=new ArrayList<CompletableFuture<HttpResponse<String>>>();long began=System.nanoTime();
        for(int i=0;i<9;i++){results.add(http.sendAsync(request(user,menu.date()),HttpResponse.BodyHandlers.ofString()));long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(service.inFlight()<i+1 && System.nanoTime()<until)Thread.sleep(5);assertEquals(i+1,service.inFlight());}
        assertTrue(fault.entered.await(1,TimeUnit.SECONDS));var rejected=http.send(request(user,menu.date()),HttpResponse.BodyHandlers.ofString());assertEquals(503,rejected.statusCode());assertEquals("AI_BUSY",json.readTree(rejected.body()).path("code").asString());
        for(var result:results){var event=events(result.get(7,TimeUnit.SECONDS).body());assertEquals(List.of("meta","error"),event.stream().map(Event::name).toList());assertEquals("AI_TIMEOUT",event.get(1).body().path("code").asString());}
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<7500,"Queueing must not reset the deadline.");assertTrue(fault.requests.get()<9,"Expired queue entries must not call the provider.");
    }
    @Test void closingTheBrowserBodyCancelsTheProviderAndReleasesItsSlot()throws Exception {
        var user=browser();var menu=menu();var fault=new Scenario("blocked-intent",menu.pasta());scenario.set(fault);var response=http.send(request(user,menu.date()),HttpResponse.BodyHandlers.ofInputStream());assertTrue(fault.entered.await(2,TimeUnit.SECONDS));assertEquals(1,service.inFlight());response.body().close();drained();assertEquals(0,service.inFlight());
    }
    @Test void revokingTheSessionCancelsPendingInferenceWithOneSessionExpiredEvent()throws Exception {
        var user=browser();var menu=menu();var fault=new Scenario("blocked-intent",menu.pasta());scenario.set(fault);var result=http.sendAsync(request(user,menu.date()),HttpResponse.BodyHandlers.ofString());assertTrue(fault.entered.await(2,TimeUnit.SECONDS));sessions.deleteById(user.identity().sessionId());var events=events(result.get(3,TimeUnit.SECONDS).body());assertEquals(List.of("meta","error"),events.stream().map(Event::name).toList());assertEquals("SESSION_EXPIRED",events.get(1).body().path("code").asString());
    }
}
