package com.webox.load;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
import io.gatling.javaapi.core.Simulation;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import tools.jackson.databind.json.JsonMapper;

/** 60-second ramp, then 600 seconds with all 200 authenticated users and their SSE streams alive. */
public class MealOrderingSimulation extends Simulation {
    private final boolean smoke=Boolean.getBoolean("webox.load.smoke");
    private final int users=smoke?Integer.getInteger("webox.load.smoke.users",10):200,seconds=smoke?30:600;
    private final JsonMapper json=new JsonMapper();
    private final AtomicInteger ready=new AtomicInteger(),created=new AtomicInteger(),conflicts=new AtomicInteger();
    private final AtomicLong steady=new AtomicLong();
    private final List<Map<String,Object>> samples=new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();
    private final Path report,state;
    private final Process application;
    private volatile Process browser;
    private final Map<String,Object> fixture;
    private volatile String monitorFailure;
    public MealOrderingSimulation() throws Exception {
        if(users!=10&&users!=200)throw new IllegalArgumentException("Smoke users must be 10 or 200.");
        String name=System.getProperty("webox.load.run",(smoke?"smoke-":"full-")+Instant.now().toString().replace(':','-'));
        if(!name.matches("[A-Za-z0-9_.-]+"))throw new IllegalArgumentException("Invalid run label.");
        report=Path.of("target/load",name).toAbsolutePath();Files.createDirectories(report.getParent());Files.createDirectory(report);
        state=Files.createTempFile("webox-load-",".json");Files.setPosixFilePermissions(state,PosixFilePermissions.fromString("rw-------"));
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xms256m","-Xmx1g","-Duser.timezone=UTC","-cp",System.getProperty("java.class.path"),"com.webox.load.LoadFixture",state.toString());
        builder.environment().remove("OPENROUTER_API_KEY");builder.environment().remove("SPRING_PROFILES_ACTIVE");
        application=builder.redirectErrorStream(true).redirectOutput(report.resolve("application.log").toFile()).start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup));
        long timeout=System.nanoTime()+Duration.ofSeconds(120).toNanos();
        while(application.isAlive()&&Files.size(state)==0&&System.nanoTime()<timeout)Thread.sleep(100);
        if(Files.size(state)==0){cleanup();throw new IllegalStateException("Isolated load fixture did not start; inspect its application log.");}
        fixture=json.readValue(Files.readString(state),Map.class);
        if(((Number)fixture.get("dishCount")).intValue()!=500)throw new IllegalStateException("Load fixture must contain 500 dishes.");
        var browserState=new LinkedHashMap<>(fixture);browserState.remove("employees");browserState.put("output",report.resolve("browser").toString());browserState.put("smoke",smoke);browserState.put("expectedActive",users);
        Path browserFile=report.resolve("browser-state.json");Files.writeString(browserFile,json.writeValueAsString(browserState));Files.setPosixFilePermissions(browserFile,PosixFilePermissions.fromString("rw-------"));
        var rows=(List<Map<String,Object>>)fixture.get("employees");var feeder=rows.subList(0,users).iterator();String date=(String)fixture.get("date"),origin=(String)fixture.get("apiOrigin");
        var protocol=http.baseUrl(origin).acceptHeader("application/json").contentTypeHeader("application/json").disableCaching().disableWarmUp();
        var scenario=scenario("Authenticated employee with one inventory stream")
            .feed(feeder).exec(addCookie(Cookie("WEBOXSESSION","#{cookie}")))
            .exec(http("Session and CSRF").get("/api/v1/auth/csrf").check(status().is(200),jsonPath("$.token").saveAs("csrf"))).exitHereIfFailed()
            .exec(sse("Inventory connection").get("/api/v1/menus/"+date+"/events").await(10).on(sse.checkMessage("Initial inventory snapshot").check(jsonPath("$.event").is("inventory.snapshot")))).exitHereIfFailed()
            .exec(session->{if(ready.incrementAndGet()==users)steady.set(System.nanoTime());return session;})
            .asLongAs(session->steady.get()==0).on(pause(1))
            .pause(1,5)
            .exec(http("Valid quote").post("/api/v1/checkout/quotes").header("X-XSRF-TOKEN","#{csrf}")
                .body(StringBody("{\"deliveryDate\":\""+date+"\",\"meal\":\"Lunch\",\"items\":[{\"dishId\":\"#{dishId}\",\"dishVersion\":1,\"quantity\":1,\"optionIds\":[]}]}"))
                .check(status().is(201),jsonPath("$.quoteId").saveAs("quoteId"))).exitHereIfFailed()
            .exec(session->session.set("key",UUID.randomUUID().toString()))
            .exec(http("Valid order").post("/api/v1/orders").header("X-XSRF-TOKEN","#{csrf}").header("Idempotency-Key","#{key}")
                .body(StringBody("{\"quoteId\":\"#{quoteId}\",\"addressText\":\"Load fixture office\"}"))
                .check(status().saveAs("orderStatus"),status().is(201),jsonPath("$.orderId").saveAs("orderId")))
            .exec(session->{if(session.contains("orderStatus")){int code=session.getInt("orderStatus");if(code==201)created.incrementAndGet();if(code==409)conflicts.incrementAndGet();}return session;}).exitHereIfFailed()
            .pause(1,5)
            .exec(http("Cancel pending order").post("/api/v1/orders/#{orderId}/cancel").header("X-XSRF-TOKEN","#{csrf}").body(StringBody("{}")).check(status().is(200))).exitHereIfFailed()
            .asLongAs(session->System.nanoTime()-steady.get()<Duration.ofSeconds(seconds+2).toNanos()).on(
                pause(1,5),exec(http("Menu page").get("/api/v1/menus/"+date).queryParam("page",session->(session.userId()%20)).check(status().is(200))),
                pause(1,5),exec(http("Search dishes").get("/api/v1/menus/"+date).queryParam("search","#{search}").check(status().is(200),jsonPath("$.totalElements").ofInt().is(1))),
                pause(1,5),exec(http("Filtered menu").get("/api/v1/menus/"+date).queryParam("categories","Light Meal").check(status().is(200))),
                pause(1,5),exec(http("Dish details").get("/api/v1/menus/"+date+"/dishes/#{dishId}").check(status().is(200))))
            .exec(sse("Close inventory connection").close());
        setUp(scenario.injectOpen(rampUsers(users).during(smoke?5:60))).protocols(protocol).maxDuration(Duration.ofSeconds(seconds+(smoke?90:150)))
            .assertions(global().failedRequests().count().is(0L),details("Valid order").successfulRequests().count().is((long)users));
    }
    @Override public void before(){monitor.scheduleAtFixedRate(()->sample((String)fixture.get("apiOrigin"),report.resolve("browser-state.json")),0,1,TimeUnit.SECONDS);}
    @Override public void after(){
        Path browserFile=report.resolve("browser-state.json");
            monitor.shutdownNow();boolean browserPassed=false;
            try{
                if(browser!=null&&browser.waitFor(10,TimeUnit.SECONDS))browserPassed=browser.exitValue()==0;
                var proof=new LinkedHashMap<String,Object>();proof.put("smokeOnly",smoke);proof.put("users",users);proof.put("dishes",500);proof.put("rampSeconds",smoke?5:60);proof.put("steadySeconds",seconds);proof.put("ready",ready.get());proof.put("validOrders",created.get());proof.put("businessConflicts",conflicts.get());proof.put("samples",samples);proof.put("browserPassed",browserPassed);proof.put("monitorFailure",monitorFailure);proof.put("separateApplicationJvm",true);proof.put("applicationHeap","256m–1g");proof.put("hikariPool",16);proof.put("sharedIpAuthenticationBurst",240);proof.put("sharedIpAuthenticationPerMinute",300);
                var closures=new LinkedHashMap<String,Double>();
                for(String reason:List.of("queue-limit","write-timeout","executor-limit","write-or-session-error","session-expired","lifetime")){
                    var request=HttpRequest.newBuilder(URI.create(fixture.get("apiOrigin")+"/actuator/metrics/webox.inventory.closed?tag=reason:"+reason)).header("Cookie","WEBOXSESSION="+fixture.get("adminCookie")).timeout(Duration.ofSeconds(3)).GET().build();
                    var reply=HttpClientHolder.CLIENT.send(request,HttpResponse.BodyHandlers.ofString());
                    if(reply.statusCode()!=200 && reply.statusCode()!=404)throw new IllegalStateException("Stream close metrics unavailable.");
                    closures.put(reason,reply.statusCode()==404?0:json.readTree(reply.body()).path("measurements").get(0).path("value").asDouble());
                }
                proof.put("streamCloseCounts",closures);
                boolean active=samples.size()>=seconds&&samples.stream().allMatch(row->((Number)row.get("activeInventoryStreams")).doubleValue()>=users);
                proof.put("passed",ready.get()==users&&created.get()==users&&active&&browserPassed&&monitorFailure==null&&closures.get("queue-limit")==0&&closures.get("write-timeout")==0&&closures.get("executor-limit")==0);
                Files.writeString(report.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
                if(!(Boolean)proof.get("passed"))throw new AssertionError("Load validation failed; inspect result.json and browser.log.");
            }catch(Exception error){throw new IllegalStateException("Load proof could not be completed.",error);}finally{try{Files.deleteIfExists(browserFile);}catch(Exception ignored){}cleanup();}
    }
    private void sample(String origin,Path browserFile){
        if(steady.get()==0)return;long elapsed=System.nanoTime()-steady.get();if(elapsed>Duration.ofSeconds(seconds).toNanos())return;
        try{
            var response=HttpClientHolder.CLIENT.send(HttpRequest.newBuilder(URI.create(origin+"/actuator/metrics/webox.inventory.active")).header("Cookie","WEBOXSESSION="+fixture.get("adminCookie")).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException();double active=json.readTree(response.body()).path("measurements").get(0).path("value").asDouble();
            samples.add(Map.of("elapsedMs",elapsed/1_000_000,"activeInventoryStreams",active));
            if(browser==null){var process=new ProcessBuilder(Path.of("../scripts/frontend.sh").toAbsolutePath().normalize().toString(),"exec","--","playwright","test","--config=playwright.performance.config.ts").redirectErrorStream(true).redirectOutput(report.resolve("browser.log").toFile());process.environment().put("WEBOX_LOAD_STATE",browserFile.toString());browser=process.start();}
        }catch(Exception failure){monitorFailure=failure.getClass().getSimpleName();}
    }
    private static class HttpClientHolder {static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();}
    private void cleanup(){
        monitor.shutdownNow();if(browser!=null&&browser.isAlive()){browser.descendants().forEach(ProcessHandle::destroy);browser.destroy();}
        if(application!=null&&application.isAlive()){application.destroy();try{if(!application.waitFor(40,TimeUnit.SECONDS))application.destroyForcibly();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        try{Files.deleteIfExists(state);}catch(Exception ignored){}
    }
}
