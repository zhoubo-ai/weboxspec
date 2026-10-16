package com.webox.inventory;

import com.webox.support.AdminTestSupport;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;

/** Two real packaged JVMs share only the disposable MySQL and media directory. */
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class InventoryRestartIT extends AdminTestSupport {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).version(HttpClient.Version.HTTP_1_1).build();
    private Process start(int port,boolean dispatch,Path log) throws Exception {
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-jar",Path.of("target/webox-api-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString(),"--server.address=127.0.0.1","--server.port="+port,"--webox.inventory.dispatch-enabled="+dispatch)
            .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DB_URL",jdbcUrl());builder.environment().put("DB_USERNAME",MYSQL.getUsername());builder.environment().put("DB_PASSWORD",MYSQL.getPassword());builder.environment().put("MEDIA_ROOT",MEDIA_ROOT.toString());builder.environment().put("SESSION_COOKIE_SECURE","false");
        Process process=builder.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(process.isAlive() && System.nanoTime()<deadline) {
            try {if(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/health")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return process;}catch(IOException ignored){}
            Thread.sleep(100);
        }
        process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new AssertionError("Packaged inventory process did not become healthy.");
    }
    private InputStream stream(int port,String date,Browser user) throws Exception {
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/menus/"+date+"/events")).header("Cookie","WEBOXSESSION="+user.identity().sessionId()).timeout(Duration.ofSeconds(4)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());assertEquals(200,response.statusCode());return response.body();
    }
    private String snapshot(InputStream stream) throws Exception {
        var executor=Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"restart-snapshot-reader");thread.setDaemon(true);return thread;});
        try {return executor.submit(()->{var reader=new BufferedReader(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));assertEquals("event: inventory.snapshot",reader.readLine());String data=reader.readLine();assertTrue(data.startsWith("data: "));return data.substring(6);}).get(4,TimeUnit.SECONDS);}
        finally {executor.shutdownNow();}
    }
    @Test void killedApplicationRetainsPendingOutboxAndRestartsWithCurrentSnapshotAndSession() throws Exception {
        var user=browser();var menu=menu();int port;try(var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())) {port=socket.getLocalPort();}
        var log=Files.createTempFile("webox-inventory-restart-",".log");Process first=null,second=null;
        try {
            first=start(port,false,log);
            try(var oldStream=stream(port,menu.date().toString(),user)) {
                var initial=json.readTree(snapshot(oldStream));assertEquals(5,initial.path("items").size());
                var order=postJson("/orders",user,Map.of("quoteId",quote(user,menu,1),"addressText","Restart office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());
                String event=jdbc.queryForObject("SELECT id FROM outbox_events WHERE aggregate_key=? AND published_at IS NULL",String.class,menu.date()+"/"+menu.pasta());assertNotNull(event);
                first.destroyForcibly();assertTrue(first.waitFor(5,TimeUnit.SECONDS));second=start(port,true,log);
                try(var restored=stream(port,menu.date().toString(),user)) {
                    var current=json.readTree(snapshot(restored));boolean found=false;for(var row:current.path("items"))if(row.path("dishId").asString().equals(menu.pasta())) {assertEquals(19,row.path("remaining").asInt());assertEquals(1,row.path("version").asLong());found=true;}assertTrue(found);
                }
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events WHERE id=?",Boolean.class,event)==false && System.nanoTime()<deadline)Thread.sleep(20);
                assertEquals(true,jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events WHERE id=?",Boolean.class,event));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE id=?",Integer.class,event));
                assertEquals(200,read("/orders/"+field(order,"orderId"),user).getStatus());
            }
        }finally {for(Process process:Arrays.asList(first,second))if(process!=null && process.isAlive()){process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);}}Files.deleteIfExists(log);}
    }
}
