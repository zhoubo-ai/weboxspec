package com.webox.recommendation;

import com.webox.support.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="OPENROUTER_API_KEY=")
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RecommendationContractIT extends AdminTestSupport {
    @LocalServerPort int port;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).version(HttpClient.Version.HTTP_1_1).build();
    private Map<String,Object> request(LocalDate date,String prompt){return Map.of("deliveryDate",date.toString(),"meal","Lunch","prompt",prompt);}
    private HttpResponse<String> recommend(Browser user,Object body,String csrf) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/recommendations/stream"))
            .header("Cookie","WEBOXSESSION="+user.identity().sessionId()).header("X-XSRF-TOKEN",csrf).header("Content-Type","application/json").header("Accept","text/event-stream")
            .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private void error(HttpResponse<String> response,int status,String code){assertEquals(status,response.statusCode());assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));ContractAssertions.matches("Error",response.body());assertEquals(code,json.readTree(response.body()).path("code").asString());}
    @Test void anEmptyRealMenuHasOneMetaAndOneNoMatchWithoutCallingAnUnavailableModel() throws Exception {
        var user=browser();var date=menu().date().plusYears(5);var response=recommend(user,request(date,"A mild lunch, please."),user.csrf());assertEquals(200,response.statusCode());assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"));assertEquals("no-store",response.headers().firstValue("Cache-Control").orElse(""));
        var events=response.body().replace("\r\n","\n").split("\n\n");assertEquals(2,events.length);assertTrue(events[0].startsWith("event: meta\ndata: "));assertTrue(events[1].startsWith("event: done\ndata: "));
        var meta=json.readTree(events[0].substring(events[0].indexOf("data: ")+6));var done=json.readTree(events[1].substring(events[1].indexOf("data: ")+6));UUID.fromString(meta.path("requestId").asString());assertEquals(date.toString(),meta.path("deliveryDate").asString());assertEquals(meta.path("requestId"),done.path("requestId"));assertEquals(0,done.path("count").asInt());assertEquals("NO_MATCH",done.path("outcome").asString());assertFalse(response.body().contains(user.identity().user().email()));
    }
    @Test void promptLimitsUseTrimmedUnicodeCodePointsWithoutTruncating() throws Exception {
        var user=browser();var date=menu().date().plusYears(5);
        assertEquals(200,recommend(user,request(date,"  "+"a".repeat(999)+"🍜  "),user.csrf()).statusCode());
        for(String prompt:List.of(" ","a".repeat(1000)+"🍜"))error(recommend(user,request(date,prompt),user.csrf()),400,"VALIDATION_ERROR");
    }
    @Test void dateMealUnknownPropertiesAndMissingFieldsAreRejectedBeforeOpeningAStream() throws Exception {
        var user=browser();var date=menu().date();
        for(Map<String,Object> body:List.<Map<String,Object>>of(Map.of("deliveryDate","2026-02-30","meal","Lunch","prompt","Lunch"),Map.of("deliveryDate",date.toString(),"meal","Brunch","prompt","Lunch"),Map.of("deliveryDate",date.toString(),"meal","Lunch")))error(recommend(user,body,user.csrf()),400,"VALIDATION_ERROR");
        for(String field:List.of("userId","provider","model","systemPrompt")){var body=new HashMap<>(request(date,"Lunch"));body.put(field,"injected");error(recommend(user,body,user.csrf()),400,"VALIDATION_ERROR");}
        error(recommend(user,request(LocalDate.of(2026,10,15),"Lunch"),user.csrf()),422,"INVALID_DELIVERY_DATE");
        TEST_CLOCK.set(Instant.parse("2026-10-16T02:00:00Z"));error(recommend(user,request(LocalDate.of(2026,10,16),"Lunch"),user.csrf()),409,"SLOT_EXPIRED");
    }
    @Test void csrfAndRevokedSessionsAreRejectedBeforeStreaming() throws Exception {
        var user=browser();var body=request(menu().date(),"Lunch");error(recommend(user,body,"wrong"),403,"CSRF_INVALID");sessions.deleteById(user.identity().sessionId());
        // A fresh anonymous CSRF token isolates authorization from the CSRF rejection above.
        var cookies=new CookieManager();var anonymous=HttpClient.newBuilder().cookieHandler(cookies).build();String base="http://127.0.0.1:"+port;
        var csrf=anonymous.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        var response=anonymous.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/recommendations/stream")).header("X-XSRF-TOKEN",json.readTree(csrf.body()).path("token").asString()).header("Content-Type","application/json").header("Accept","text/event-stream").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());error(response,401,"UNAUTHENTICATED");
    }
    @Test void missingProviderFailsExplicitlyWhileOrdinaryOrderingRemainsAvailable() throws Exception {
        var user=browser();var menu=menu();error(recommend(user,request(menu.date(),"A mild lunch."),user.csrf()),503,"AI_UNAVAILABLE");assertEquals(200,read("/menus/"+menu.date(),user).getStatus());assertEquals(201,postJson("/orders",user,Map.of("quoteId",quote(user,menu,1),"addressText","Ordinary ordering office"),UUID.randomUUID().toString()).getStatus());
    }
}
