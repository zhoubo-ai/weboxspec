package com.webox.shared;
import com.webox.support.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.mysql.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;
/** Real packaged JVM restarts, a real MySQL restart, and restoration into a separate labelled container. */
@Tag("persistence")
@SpringBootTest(properties="spring.session.jdbc.cleanup-cron=-")
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class PersistenceIT extends AdminTestSupport {
    @Autowired MenuSeeder seeder;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final Path reports=Path.of("target/persistence").toAbsolutePath();
    private int freePort()throws IOException {try(var socket=new ServerSocket(0,0,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    // Docker Desktop can assign a new ephemeral host port on restart; Testcontainers caches the original inspection.
    private String currentJdbcUrl(MySQLContainer database) {
        var binding=database.getCurrentContainerInfo().getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(3306));
        if(binding==null||binding.length==0)throw new IllegalStateException("Container port is not published yet.");
        return "jdbc:mysql://"+database.getHost()+":"+binding[0].getHostPortSpec()+"/"+database.getDatabaseName()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=1000&socketTimeout=2000";
    }
    private ProcessBuilder application(MySQLContainer database,Path media,int port,boolean seed,String email) {
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Duser.timezone=UTC","-jar",Path.of("target/webox-api-0.1.0-SNAPSHOT.jar").toAbsolutePath().toString(),"--server.address=127.0.0.1","--server.port="+port));if(seed)command.add("--spring.profiles.active=seed");
        var process=new ProcessBuilder(command);var environment=process.environment();environment.remove("OPENROUTER_API_KEY");environment.remove("SPRING_PROFILES_ACTIVE");environment.put("DB_URL",currentJdbcUrl(database));environment.put("DB_USERNAME",database.getUsername());environment.put("DB_PASSWORD",database.getPassword());environment.put("MEDIA_ROOT",media.toString());environment.put("SESSION_COOKIE_SECURE","false");environment.put("ADMIN_EMAIL",email);environment.put("ADMIN_INITIAL_PASSWORD","SeedMustNotReset123");return process;
    }
    private Process start(MySQLContainer database,Path media,int port,String stage,String adminEmail)throws Exception {
        var process=application(database,media,port,false,adminEmail).redirectErrorStream(true).redirectOutput(reports.resolve(stage+"-application.log").toFile()).start();long deadline=System.nanoTime()+Duration.ofSeconds(40).toNanos();
        while(process.isAlive()&&System.nanoTime()<deadline){try{if(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/health")).timeout(Duration.ofSeconds(1)).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()==200)return process;}catch(IOException ignored){}Thread.sleep(100);}
        stop(process);throw new AssertionError("Packaged application did not start for "+stage);
    }
    private void stop(Process process)throws Exception {if(process==null||!process.isAlive())return;process.destroy();if(!process.waitFor(40,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new AssertionError("Application did not stop gracefully.");}}
    private void browsers(Map<String,Object> shared,int port,String stage)throws Exception {
        var state=new LinkedHashMap<String,Object>(shared);state.put("apiOrigin","http://127.0.0.1:"+port);state.put("browserOutput",reports.resolve(stage+"-browser").toString());Path handoff=Files.createTempFile("webox-persistence-state-",".json");Files.setPosixFilePermissions(handoff,PosixFilePermissions.fromString("rw-------"));
        try {Files.writeString(handoff,json.writeValueAsString(state));var process=new ProcessBuilder(Path.of("../scripts/frontend.sh").toAbsolutePath().normalize().toString(),"exec","--","playwright","test","--config=playwright.persistence.config.ts").redirectErrorStream(true).redirectOutput(reports.resolve(stage+"-browser.log").toFile());process.environment().put("WEBOX_PERSISTENCE_STATE",handoff.toString());var running=process.start();if(!running.waitFor(120,TimeUnit.SECONDS)){running.descendants().forEach(ProcessHandle::destroy);running.destroy();if(!running.waitFor(10,TimeUnit.SECONDS)){running.descendants().forEach(ProcessHandle::destroyForcibly);running.destroyForcibly();}throw new AssertionError("Persistence browser stage timed out: "+stage);}assertEquals(0,running.exitValue(),"Three-browser persistence stage: "+stage);
        } finally {Files.deleteIfExists(handoff);}
    }
    private void storage(String mode,MySQLContainer database,Path media,Path backup,String report)throws Exception {
        var process=new ProcessBuilder(Path.of("../infra/"+mode+".sh").toAbsolutePath().normalize().toString(),"--container",database.getContainerId(),"--database",database.getDatabaseName(),"--media-root",media.toString(),mode.equals("backup")?"--output":"--from-backup",backup.toString()).redirectErrorStream(true).redirectOutput(reports.resolve(report+".log").toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Storage operation timed out.");}assertEquals(0,process.exitValue(),report);
    }
    private void databaseReady(MySQLContainer database)throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(40).toNanos();while(System.nanoTime()<deadline){try(var connection=DriverManager.getConnection(currentJdbcUrl(database),database.getUsername(),database.getPassword());var query=connection.createStatement()){query.execute("SELECT 1");return;}catch(java.sql.SQLException | IllegalStateException ignored){Thread.sleep(100);}}throw new AssertionError("Restarted MySQL did not become ready.");
    }
    @Test void normalRestartsAndAnIsolatedBackupRestorePreserveAllPersistentDomains()throws Exception {
        Files.createDirectories(reports);var owner=browser();var revoked=browser();var admin=admin();var menu=menu();seeder.initialize(admin.identity().user());String seedDish=MenuSeeder.dishId("tomato-pasta");jdbc.update("UPDATE dishes SET base_price_cents=3211,version=version+1 WHERE id=?",seedDish);
        var preferences=new LinkedHashMap<String,Object>();preferences.put("allergens",List.of("Egg"));preferences.put("cuisines",List.of("Western"));preferences.put("spiceLevel","Mild");preferences.put("flavor","Light");preferences.put("budgetMinCents",2000);preferences.put("budgetMaxCents",4000);preferences.put("expectedVersion",0);assertEquals(200,write("PUT","/me/preferences",owner,preferences).getStatus());String quote=quote(owner,menu,2),key=UUID.randomUUID().toString(),address="上海市 A 楼 3 层 — Persistence office";var placed=postJson("/orders",owner,Map.of("quoteId",quote,"addressText",address),key);assertEquals(201,placed.getStatus());assertEquals(204,postJson("/auth/logout",revoked,null,null).getStatus());jdbc.update("UPDATE dishes SET base_price_cents=3001,version=version+1 WHERE id=?",menu.pasta());
        var state=new LinkedHashMap<String,Object>();state.put("session",owner.identity().sessionId());state.put("revokedSession",revoked.identity().sessionId());state.put("email",owner.identity().user().email());state.put("password","Fixture123"+owner.identity().user().email().split("@")[0]);state.put("date",menu.date().toString());state.put("orderId",field(placed,"orderId"));state.put("quoteId",quote);state.put("key",key);state.put("address",address);state.put("addresses",json.readTree(responseBody(read("/me/addresses",owner))));
        String adminHash=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,admin.identity().user().userId());long dishes=jdbc.queryForObject("SELECT COUNT(*) FROM dishes",Long.class);Path backup=reports.resolve("backup-"+UUID.randomUUID()),restoredMedia=Files.createTempDirectory("webox-restored-media-");Process running=null;MySQLContainer restored=null;var passed=new ArrayList<String>();
        try {
            int port=freePort();running=start(MYSQL,MEDIA_ROOT,port,"baseline",admin.identity().user().email());browsers(state,port,"baseline");passed.add("baseline");stop(running);running=null;
            running=start(MYSQL,MEDIA_ROOT,port,"application-restart",admin.identity().user().email());browsers(state,port,"application-restart");passed.add("application-restart");stop(running);running=null;
            ((com.zaxxer.hikari.HikariDataSource)jdbc.getDataSource()).close();
            MYSQL.getDockerClient().restartContainerCmd(MYSQL.getContainerId()).withTimeout(30).exec();databaseReady(MYSQL);jdbc=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(currentJdbcUrl(MYSQL),MYSQL.getUsername(),MYSQL.getPassword()));running=start(MYSQL,MEDIA_ROOT,port,"mysql-restart",admin.identity().user().email());browsers(state,port,"mysql-restart");passed.add("mysql-restart");stop(running);running=null;
            var seed=application(MYSQL,MEDIA_ROOT,freePort(),true,admin.identity().user().email()).redirectErrorStream(true).redirectOutput(reports.resolve("seed-rerun.log").toFile()).start();assertTrue(seed.waitFor(40,TimeUnit.SECONDS));assertEquals(0,seed.exitValue());assertEquals(adminHash,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,admin.identity().user().userId()));assertEquals(dishes,jdbc.queryForObject("SELECT COUNT(*) FROM dishes",Long.class));assertEquals(3211,jdbc.queryForObject("SELECT base_price_cents FROM dishes WHERE id=?",Long.class,seedDish));assertEquals(18,jdbc.queryForObject("SELECT total_quantity-allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,menu.date(),menu.pasta()));passed.add("seed-idempotency");
            storage("backup",MYSQL,MEDIA_ROOT,backup,"backup");String restoreName="webox_restore_"+UUID.randomUUID().toString().replace("-","");restored=new MySQLContainer(MYSQL_IMAGE).withDatabaseName(restoreName).withUsername("webox_restore").withPassword(UUID.randomUUID().toString()).withLabel("webox.restore-target","true").withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_0900_ai_ci","--default-time-zone=+00:00");restored.start();storage("restore",restored,restoredMedia,backup,"restore");int restorePort=freePort();running=start(restored,restoredMedia,restorePort,"backup-restore",admin.identity().user().email());browsers(state,restorePort,"backup-restore");passed.add("isolated-backup-restore");
            var repeated=new ProcessBuilder(Path.of("../infra/restore.sh").toAbsolutePath().normalize().toString(),"--container",restored.getContainerId(),"--database",restoreName,"--media-root",restoredMedia.toString(),"--from-backup",backup.toString()).redirectErrorStream(true).redirectOutput(reports.resolve("overwrite-refused.log").toFile()).start();assertTrue(repeated.waitFor(20,TimeUnit.SECONDS));assertNotEquals(0,repeated.exitValue());passed.add("nonempty-target-refused");
            Files.writeString(reports.resolve("result.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("passed",passed,"browserProjects",List.of("chromium","firefox","webkit"),"browserChecksPerStage",6,"browserStages",4,"mysqlImage",MYSQL_IMAGE,"normalAppShutdown",true,"sourceAndRestoreIsolated",true,"backupManifestSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backup.resolve("manifest.json")))))));
        } finally {stop(running);if(restored!=null)restored.stop();try(var paths=Files.walk(restoredMedia)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
