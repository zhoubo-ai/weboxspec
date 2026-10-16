package com.webox.recommendation;
import com.webox.auth.*;
import com.webox.shared.DomainTypes.*;
import com.webox.support.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

/** Real OpenRouter functional acceptance; independent browser performance samples use the same frozen manifest. */
@Tag("llm-live")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@org.springframework.context.annotation.Import(LiveRecommendationIT.Diagnostics.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class LiveRecommendationIT extends AdminTestSupport {
    private static final class Capture {volatile IntentService.Extracted intent;final List<RecommendationModels.Generated> generated=new java.util.concurrent.CopyOnWriteArrayList<>();}
    private static volatile Capture capture=new Capture();
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class Diagnostics {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        OpenRouterProvider observedProvider(OpenRouterConfiguration config,tools.jackson.databind.json.JsonMapper json){return new OpenRouterProvider(config,json){
            @Override public Result<IntentService.Extracted> extract(String prompt,LocalDate today,RequestDeadline deadline,java.util.function.Consumer<Metadata> observation){var current=capture;var result=super.extract(prompt,today,deadline,observation);current.intent=result.value();return result;}
            @Override public Result<List<RecommendationModels.Generated>> recommend(String prompt,IntentService.Intent intent,CandidateService.Prepared prepared,RequestDeadline deadline,java.util.function.Consumer<RecommendationModels.Generated> consumer,java.util.function.Consumer<Metadata> observation){var current=capture;return super.recommend(prompt,intent,prepared,deadline,item->{current.generated.add(item);consumer.accept(item);},observation);}
        };}
    }
    @LocalServerPort int port;@Autowired OpenRouterConfiguration config;@Autowired RecommendationObservations observations;@Autowired PreferencesService preferences;
    private final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private void history(Browser user,CatalogFixtures.Menu menu,LocalDate date,String dish,String...options)throws Exception {
        TEST_CLOCK.set(date.atTime(8,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());
        jdbc.update("INSERT IGNORE INTO daily_menus VALUES (?,?,?,?)",date,1,menu.adminId(),java.sql.Timestamp.from(TEST_CLOCK.instant()));
        jdbc.update("INSERT INTO daily_supply SELECT ?,dish_id,scheduled,total_quantity,0,0,updated_at FROM daily_supply WHERE delivery_date=?",date,menu.date());
        var quote=postJson("/checkout/quotes",user,Map.of("deliveryDate",date.toString(),"meal","Lunch","items",List.of(line(dish,1,1,options))),null);assertEquals(201,quote.getStatus());
        assertEquals(201,postJson("/orders",user,Map.of("quoteId",field(quote,"quoteId"),"addressText","Live acceptance fixture office"),UUID.randomUUID().toString()).getStatus());TEST_CLOCK.set(INITIAL_TIME);
    }
    private Map<String,Object> execute(JsonNode item)throws Exception {
        var user=browser();var menu=menu();String setup=item.path("setup").asString();String userId=user.identity().user().userId();var identifiers=Map.of("burger",menu.burger(),"pasta",menu.pasta(),"chinese",menu.chinese(),"japanese",menu.japanese());
        var allergens=setup.equals("dairy")?List.of(Allergen.Dairy):setup.equals("gluten")?List.of(Allergen.Gluten):List.<Allergen>of();
        if(!allergens.isEmpty() || setup.equals("western-light") || setup.equals("budget"))preferences.replace(userId,new PreferencesModels.Write(allergens,setup.equals("western-light")?List.of(Category.Western):List.of(),null,setup.equals("western-light")?Flavor.Light:null,setup.equals("budget")?0L:null,setup.equals("budget")?2000L:null,0L));
        if(setup.equals("pasta-sold-out"))jdbc.update("UPDATE daily_supply SET total_quantity=0,version=version+1 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.pasta());
        if(setup.equals("history")){history(user,menu,LocalDate.of(2026,10,6),menu.burger(),menu.options().get("Plain bun"),menu.options().get("Ketchup"));history(user,menu,LocalDate.of(2026,10,15),menu.pasta(),menu.options().get("Spaghetti"));}
        LocalDate target=setup.equals("empty")?menu.date().plusYears(20):menu.date();var allowed=new HashSet<String>();item.path("allowed").forEach(value->allowed.add(identifiers.get(value.asString())));
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/recommendations/stream")).timeout(Duration.ofSeconds(25)).header("Cookie","WEBOXSESSION="+user.identity().sessionId()).header("X-XSRF-TOKEN",user.csrf()).header("Content-Type","application/json").header("Accept","text/event-stream").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("deliveryDate",target.toString(),"meal","Lunch","prompt",item.path("prompt").asString())))).build();
        var captured=new Capture();capture=captured;long start=System.nanoTime();var report=new LinkedHashMap<String,Object>();report.put("case",item.path("id").asString());report.put("expected",item.path("expected").asString());report.put("deliveryDate",target);var cards=new ArrayList<JsonNode>();var problems=new ArrayList<String>();String requestId="",outcome="NO_TERMINAL";int terminals=0;
        try {
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());report.put("httpStatus",response.statusCode());
            if(response.statusCode()!=200){outcome=json.readTree(response.body()).path("code").asString();problems.add("HTTP "+response.statusCode());}
            else for(String block:response.body().split("\n\n"))if(block.startsWith("event: ")) {
                int split=block.indexOf("\ndata: ");String name=block.substring(7,split);var body=json.readTree(block.substring(split+7));
                if(name.equals("meta")){if(!requestId.isEmpty())problems.add("Duplicate meta");requestId=body.path("requestId").asString();}
                else if(!requestId.equals(body.path("requestId").asString()))problems.add("Request ID mismatch");
                if(name.equals("card")) {
                    ContractAssertions.matches("RecommendationCard",body.toString());if(terminals!=0 || body.path("index").asInt()!=cards.size())problems.add("Invalid card order");String id=body.path("dish").path("dishId").asString();if(!allowed.contains(id))problems.add("Dish outside independently registered allowed set");
                    var full=body.path("dish");var labels=new HashSet<String>();full.path("allergens").forEach(a->labels.add(a.asString()));full.path("optionGroups").forEach(group->group.path("options").forEach(option->option.path("allergens").forEach(a->labels.add(a.asString()))));for(var allergen:allergens)if(labels.contains(allergen.name()))problems.add("Allergen exclusion failed");
                    if(full.path("remaining").asLong()<=0)problems.add("Unavailable card");cards.add(body);
                }
                if(name.equals("done")){ContractAssertions.matches("RecommendationDone",body.toString());terminals++;outcome=body.path("outcome").asString();if(body.path("count").asInt()!=cards.size())problems.add("Count mismatch");}
                if(name.equals("error")){ContractAssertions.matches("RecommendationError",body.toString());terminals++;outcome=body.path("code").asString();if(body.path("partial").asBoolean()!=!cards.isEmpty())problems.add("Partial mismatch");}
            }
        }catch(Exception failure){outcome="CLIENT_FAILURE";problems.add(failure.getClass().getSimpleName());}
        report.put("elapsedMillis",(System.nanoTime()-start)/1_000_000);report.put("actual",outcome);report.put("cards",cards);
        if(terminals!=1)problems.add("Expected exactly one terminal event");if(!outcome.equals(item.path("expected").asString()))problems.add("Unexpected outcome");
        if(outcome.equals("COMPLETE") && (cards.isEmpty() || cards.size()>3))problems.add("COMPLETE card count");
        var observation=observations.find(requestId);report.put("server",observation.orElse(null));
        if(outcome.equals("COMPLETE") && (observation.isEmpty() || observation.get().intent()==null || observation.get().recommendation()==null || observation.get().intent().generationId().isEmpty() || observation.get().recommendation().generationId().isEmpty()))problems.add("Missing real provider evidence");
        report.put("extractedIntent",captured.intent);report.put("generatedItems",List.copyOf(captured.generated));report.put("problems",problems);report.put("passed",problems.isEmpty());return report;
    }
    @Test void frozenFunctionalCasesUseRealInferenceAndRetainEveryAttempt()throws Exception {
        assertTrue(config.available(),"OPENROUTER_API_KEY is required for llm-live");assertEquals("https",config.endpoint().getScheme());assertEquals("openrouter.ai",config.endpoint().getHost());
        var path=Path.of("src/test/resources/recommendation/live-cases.json");byte[] bytes=Files.readAllBytes(path);var manifest=json.readTree(bytes);assertEquals(manifest.path("model").asString(),config.model());assertTrue(manifest.path("functional").size()>=20);assertTrue(manifest.path("performance").size()>=20);
        var rows=new ArrayList<Map<String,Object>>();var report=new LinkedHashMap<String,Object>();report.put("manifestSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));report.put("beganAt",Instant.now());report.put("java",System.getProperty("java.version"));report.put("os",System.getProperty("os.name"));report.put("architecture",System.getProperty("os.arch"));report.put("configuredModel",config.model());report.put("concurrency",1);report.put("warmup",execute(manifest.path("functional").get(0)));report.put("functional",rows);
        String run=System.getenv().getOrDefault("WEBOX_LIVE_RUN","initial");assertTrue(run.matches("[A-Za-z0-9_-]{1,50}"));var destination=Path.of("target/failsafe-live-reports/recommendation-functional-"+run+".json");Files.createDirectories(destination.getParent());assertFalse(Files.exists(destination),"Use a new WEBOX_LIVE_RUN label; previous attempts must be retained.");
        for(var item:manifest.path("functional")){try{rows.add(execute(item));}catch(Exception | AssertionError failed){rows.add(Map.of("case",item.path("id").asString(),"expected",item.path("expected").asString(),"actual","SETUP_OR_ASSERTION_FAILURE","passed",false,"problems",List.of(failed.getClass().getSimpleName())));}Files.writeString(destination,json.writerWithDefaultPrettyPrinter().writeValueAsString(report));}
        assertEquals(manifest.path("functional").size(),rows.size());assertTrue(rows.stream().allMatch(row->Boolean.TRUE.equals(row.get("passed"))),"Some real-model cases failed; see the retained per-attempt report.");
    }
}
