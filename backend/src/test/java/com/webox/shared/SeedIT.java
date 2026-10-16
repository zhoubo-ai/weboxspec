package com.webox.shared;
import com.webox.support.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class SeedIT extends AdminTestSupport {
    @Autowired AdminInitializer admins;@Autowired MenuSeeder seeder;private final PasswordEncoder passwords=new org.springframework.security.crypto.argon2.Argon2PasswordEncoder(16,32,1,19456,2);
    @Test void explicitSeedCreatesNineDishesForBothDatesAndNeverResetsRealOperationsOrCredentials() throws Exception {
        LocalDate date=LocalDate.of(2042,4,10);TEST_CLOCK.set(date.atTime(8,0).atZone(BusinessClock.ZONE).toInstant());String email=UUID.randomUUID()+"@example.com",initial="InitialExample123",edited="EditedExample123";
        var environment=new MockEnvironment().withProperty("ADMIN_EMAIL",email).withProperty("ADMIN_INITIAL_PASSWORD",initial);var context=org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        var seed=new SeedApplication(admins,seeder,environment,context);seed.run();
        for(LocalDate day:List.of(date,date.plusDays(1))) {assertEquals(9,jdbc.queryForObject("SELECT COUNT(*) FROM daily_supply WHERE delivery_date=? AND scheduled=TRUE AND total_quantity=20 AND allocated_quantity=0",Integer.class,day));}
        var expected=new LinkedHashMap<String,Long>();expected.put("Kung Pao Chicken",2200L);expected.put("Caesar Salad",2850L);expected.put("Salmon Sashimi Set",4500L);expected.put("Tomato Pasta",2650L);expected.put("Tom Yum Soup",3200L);expected.put("Chicken Quinoa Bowl",3580L);expected.put("Mapo Tofu",1800L);expected.put("Korean Bibimbap",3000L);expected.put("Classic Beef Burger",3800L);
        for(var entry:expected.entrySet()) {
            String id=MenuSeeder.dishId(entry.getKey().toLowerCase(Locale.ROOT).replace(' ','-'));var row=jdbc.queryForMap("SELECT name,base_price_cents,is_listed,media_id FROM dishes WHERE id=?",id);assertEquals(entry.getKey(),row.get("name"));assertEquals(entry.getValue(),((Number)row.get("base_price_cents")).longValue());assertEquals(true,row.get("is_listed"));
            var asset=media.find((String)row.get("media_id")).orElseThrow();assertNotNull(javax.imageio.ImageIO.read(MEDIA_ROOT.resolve(asset.storageKey()).toFile()));assertEquals(asset.bytes(),java.nio.file.Files.size(MEDIA_ROOT.resolve(asset.storageKey())));
        }
        assertEquals(List.of("Fusilli","Penne","Spaghetti"),jdbc.queryForList("SELECT o.name FROM customization_options o JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=? AND g.name='Pasta Type' ORDER BY o.name",String.class,MenuSeeder.dishId("tomato-pasta")));
        assertEquals(List.of("BBQ","Ketchup","Mayo","Mustard"),jdbc.queryForList("SELECT o.name FROM customization_options o JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=? AND g.name='Sauce' ORDER BY o.name",String.class,MenuSeeder.dishId("classic-beef-burger")));
        assertEquals(List.of("Dairy","Gluten"),jdbc.queryForList("SELECT allergen FROM dish_allergens WHERE dish_id=? ORDER BY allergen",String.class,MenuSeeder.dishId("classic-beef-burger")));
        assertEquals("Egg",jdbc.queryForObject("SELECT a.allergen FROM option_allergens a JOIN customization_options o ON o.id=a.option_id JOIN customization_groups g ON g.id=o.group_id WHERE g.dish_id=? AND o.name='Mayo'",String.class,MenuSeeder.dishId("classic-beef-burger")));
        var adminId=auth.login(email,initial).userId();String pasta=jdbc.queryForObject("SELECT id FROM dishes WHERE name='Tomato Pasta' AND id IN (SELECT dish_id FROM daily_supply WHERE delivery_date=?)",String.class,date);
        long version=jdbc.queryForObject("SELECT version FROM dishes WHERE id=?",Long.class,pasta);String spaghetti=jdbc.queryForObject("SELECT o.id FROM customization_options o JOIN customization_groups g ON o.group_id=g.id WHERE g.dish_id=? AND o.name='Spaghetti'",String.class,pasta);
        var user=browser();var quoted=postJson("/checkout/quotes",user,Map.of("deliveryDate",date.toString(),"meal","Lunch","items",List.of(line(pasta,version,1,spaghetti))),null);assertEquals(201,quoted.getStatus());assertEquals(201,postJson("/orders",user,Map.of("quoteId",field(quoted,"quoteId"),"addressText","Office"),UUID.randomUUID().toString()).getStatus());
        jdbc.update("UPDATE dishes SET base_price_cents=2701,version=version+1 WHERE id=?",pasta);jdbc.update("UPDATE daily_supply SET total_quantity=30,version=version+1 WHERE delivery_date=? AND dish_id=?",date,pasta);jdbc.update("UPDATE daily_supply SET scheduled=FALSE,total_quantity=0,version=version+1 WHERE delivery_date=? AND dish_id=?",date.plusDays(1),pasta);jdbc.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(edited),adminId);
        long dishesBefore=jdbc.queryForObject("SELECT COUNT(*) FROM dishes",Long.class),mediaBefore=jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class),eventsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class);
        seed.run();assertEquals(dishesBefore,jdbc.queryForObject("SELECT COUNT(*) FROM dishes",Long.class));assertEquals(mediaBefore,jdbc.queryForObject("SELECT COUNT(*) FROM media_assets",Long.class));assertEquals(eventsBefore,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class));assertEquals(2701,jdbc.queryForObject("SELECT base_price_cents FROM dishes WHERE id=?",Integer.class,pasta));assertEquals(30,jdbc.queryForObject("SELECT total_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,date,pasta));assertEquals(1,jdbc.queryForObject("SELECT allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,date,pasta));assertEquals(false,jdbc.queryForObject("SELECT scheduled FROM daily_supply WHERE delivery_date=? AND dish_id=?",Boolean.class,date.plusDays(1),pasta));assertEquals(adminId,auth.login(email,edited).userId());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE delivery_date=? AND status='Completed'",Integer.class,date));
        assertEquals(9,jdbc.queryForObject("SELECT COUNT(*) FROM daily_supply WHERE delivery_date=?",Integer.class,date));
        // A deliberately empty operational menu must also survive initialization.
        var emptyDay=date.plusDays(2);jdbc.update("INSERT INTO daily_menus VALUES (?,?,?,?)",emptyDay,1,adminId,java.sql.Timestamp.from(TEST_CLOCK.instant()));TEST_CLOCK.set(emptyDay.atTime(8,0).atZone(BusinessClock.ZONE).toInstant());seed.run();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM daily_supply WHERE delivery_date=?",Integer.class,emptyDay));assertEquals(9,jdbc.queryForObject("SELECT COUNT(*) FROM daily_supply WHERE delivery_date=?",Integer.class,emptyDay.plusDays(1)));
    }
    @Test void packagedApplicationSeedsFromACleanDirectoryWithoutSourceDownloadsOrWebServer() throws Exception {
        var directory=java.nio.file.Files.createTempDirectory("webox-clean-seed-");var log=directory.resolve("seed.log");
        try {
            var jar=directory.resolve("webox.jar");java.nio.file.Files.copy(java.nio.file.Path.of("target/webox-api-0.1.0-SNAPSHOT.jar"),jar);
            try(var archive=new java.util.jar.JarFile(jar.toFile())) {assertNotNull(archive.getEntry("BOOT-INF/classes/seed/menu.json"));for(String slug:List.of("kung-pao-chicken","caesar-salad","salmon-sashimi-set","tomato-pasta","tom-yum-soup","chicken-quinoa-bowl","mapo-tofu","korean-bibimbap","classic-beef-burger"))assertNotNull(archive.getEntry("BOOT-INF/classes/seed/images/"+slug+".png"));}
            String email=UUID.randomUUID()+"@example.com";var executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
            for(int attempt=0;attempt<2;attempt++) {
                var builder=new ProcessBuilder(executable,"-jar",jar.toString(),"--spring.profiles.active=seed","--spring.main.web-application-type=none").directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
                builder.environment().put("DB_URL",jdbcUrl());builder.environment().put("DB_USERNAME",MYSQL.getUsername());builder.environment().put("DB_PASSWORD",MYSQL.getPassword());builder.environment().put("MEDIA_ROOT",MEDIA_ROOT.toString());builder.environment().put("ADMIN_EMAIL",email);builder.environment().put("ADMIN_INITIAL_PASSWORD","IsolatedSeed123");
                var child=builder.start();boolean done=child.waitFor(40,java.util.concurrent.TimeUnit.SECONDS);if(!done)child.destroyForcibly();assertTrue(done,"Seed process must exit, not start a web server.");assertEquals(0,child.exitValue(),"Clean packaged seed failed; no credentials are included in this assertion.");
            }
            assertEquals(com.webox.shared.DomainTypes.Role.Admin,auth.login(email,"IsolatedSeed123").role());
        }finally {try(var files=java.nio.file.Files.walk(directory)){for(var path:files.sorted(Comparator.reverseOrder()).toList())java.nio.file.Files.deleteIfExists(path);}}
    }
}
