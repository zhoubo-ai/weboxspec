package com.webox.catalog;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import com.webox.shared.DomainTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import com.webox.support.*;
import com.webox.auth.AuthService;
import com.webox.media.MediaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** Cache mechanics; real DB snapshot/visibility tests are added with MenuService. */
@SpringBootTest
class MenuCacheIT extends MySqlTestSupport {
    @Autowired JdbcTemplate jdbc;@Autowired MediaRepository media;@Autowired JdbcIndexedSessionRepository sessions;@Autowired AuthService auth;@Autowired PlatformTransactionManager manager;
    @Autowired MenuService menus;@Autowired MenuDetailsCache cache;@Autowired DishRepository dishes;@Autowired MenuQueryRepository query;
    @Autowired AdminDishService administration;
    com.webox.auth.UserIdentity actor(CatalogFixtures.Menu fixture) {return new com.webox.auth.UserIdentity(fixture.adminId(),jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,fixture.adminId()),Role.Admin);}
    AdminCatalogModels.DishUpdate changed(AdminCatalogModels.AdminDish dish) {
        var groups=dish.optionGroups().stream().map(g->new AdminCatalogModels.GroupWrite(g.groupId(),g.name(),g.required(),g.selectionMode(),g.options().stream().map(o->new AdminCatalogModels.OptionWrite(o.optionId(),o.name(),o.name().equals("Cheese")?400L:o.extraCents(),o.name().equals("Cheese")?List.of(Allergen.Dairy,Allergen.Egg):o.allergens())).toList())).toList();
        return new AdminCatalogModels.DishUpdate(dish.name(),dish.description(),3000L,dish.category(),dish.protein(),dish.allergens(),dish.spiceLevel(),dish.mediaId(),groups,dish.dishVersion());
    }
    CatalogFixtures.Menu fixture() { return new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,manager).create(); }
    static MenuModels.Details dish(String id,long version) { return new MenuModels.Details(id,"Garden Salad","Fresh vegetables.","/media/00000000-0000-4000-8000-000000000001",100,Category.LightMeal,"Chickpeas",List.of(),SpiceLevel.None,List.of(),version); }
    @Test void versionedKeysCannotReplaceNewDetailsWithLateOldResults() {
        var clock=new AtomicLong();var cache=new MenuDetailsCache(Duration.ofMinutes(5),10000,clock::get);var loads=new AtomicInteger();
        assertEquals(2,cache.get("dish",2,()->{loads.incrementAndGet();return dish("dish",2);}).dishVersion());
        cache.get("dish",1,()->dish("dish",1));
        assertEquals(2,cache.get("dish",2,()->{throw new AssertionError("Must reuse version2");}).dishVersion());assertEquals(1,loads.get());
        clock.set(Duration.ofMinutes(5).toNanos());cache.get("dish",2,()->{loads.incrementAndGet();return dish("dish",2);});assertEquals(2,loads.get());
    }
    @Test void cacheIsBoundedAndNewInstanceStartsCold() {
        var cache=new MenuDetailsCache(Duration.ofMinutes(5),10000,()->0L);
        for(int i=0;i<10002;i++) {String id="dish"+i;cache.get(id,1,()->dish(id,1));}
        cache.cleanUp();assertTrue(cache.size()<=10000);assertEquals(0,new MenuDetailsCache(Duration.ofMinutes(5),10000,()->0L).size());
        assertTrue(cache.stats().evictionCount()>=2);
    }
    @Test void oldSnapshotLoadsMatchingChildrenAndCannotReplaceNewVersion() {
        var fixture=fixture();var old=new TransactionTemplate(manager);old.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);old.setReadOnly(true);
        var writer=new TransactionTemplate(manager);writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        old.executeWithoutResult(status->{
            var member=query.member(fixture.date(),fixture.pasta()).orElseThrow();assertEquals(1,member.dishVersion());
            writer.executeWithoutResult(write->{
                administration.update(actor(fixture),fixture.pasta(),changed(administration.get(fixture.pasta())));
            });
            writer.executeWithoutResult(read->{var current=menus.dish(fixture.date(),fixture.pasta());assertEquals(2,current.dishVersion());assertEquals(400,current.optionGroups().get(1).options().get(0).extraCents());});
            var before=cache.get(member.dishId(),member.dishVersion(),()->dishes.details(member.dishId(),member.dishVersion()));
            assertEquals(2650,before.basePriceCents());assertEquals(300,before.optionGroups().get(1).options().get(0).extraCents());
        });
        var after=menus.dish(fixture.date(),fixture.pasta());assertEquals(2,after.dishVersion());assertEquals(3000,after.basePriceCents());assertEquals(400,after.optionGroups().get(1).options().get(0).extraCents());assertEquals(List.of(Allergen.Dairy,Allergen.Egg),after.optionGroups().get(1).options().get(0).allergens());
    }
    @Test void cachedDetailsNeverSupplyMembershipInventoryOrMenuRevisionAndColdReadsMatch() {
        var fixture=fixture();var before=menus.dish(fixture.date(),fixture.pasta());assertEquals(20,before.remaining());long hits=cache.stats().hitCount();
        jdbc.update("UPDATE daily_supply SET allocated_quantity=3,version=version+1 WHERE delivery_date=? AND dish_id=?",fixture.date(),fixture.pasta());
        var after=menus.dish(fixture.date(),fixture.pasta());assertEquals(17,after.remaining());assertEquals(1,after.inventoryVersion());assertTrue(cache.stats().hitCount()>hits);
        var cold=new MenuDetailsCache(Duration.ofMinutes(5),10000,()->0L);var coldService=new MenuService(query,dishes,new com.webox.inventory.SupplyRepository(jdbc),cold,new com.webox.shared.BusinessClock(TEST_CLOCK),new com.webox.auth.PreferencesRepository(jdbc));
        var read=new TransactionTemplate(manager);read.setReadOnly(true);read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertEquals(after,read.execute(status->coldService.dish(fixture.date(),fixture.pasta())));assertEquals(1,cold.stats().missCount());
        jdbc.update("UPDATE daily_supply SET scheduled=FALSE,version=version+1 WHERE delivery_date=? AND dish_id=?",fixture.date(),fixture.pasta());jdbc.update("UPDATE daily_menus SET revision=revision+1 WHERE delivery_date=?",fixture.date());
        assertThrows(com.webox.shared.ApiException.class,()->menus.dish(fixture.date(),fixture.pasta()));
        var page=menus.page(fixture.date(),List.of(),"",0,24);assertEquals(2,page.menuRevision());assertTrue(page.items().stream().noneMatch(d->d.dishId().equals(fixture.pasta())));
    }
    @Test void evictionIsDeferredUntilCommitAndRollbackPreservesTheEntry() {
        var fixture=fixture();menus.dish(fixture.date(),fixture.pasta());var write=new TransactionTemplate(manager);
        long misses=cache.stats().missCount();
        write.executeWithoutResult(status->{cache.invalidateAfterCommit(java.util.Set.of(fixture.pasta()));status.setRollbackOnly();});
        menus.dish(fixture.date(),fixture.pasta());assertEquals(misses,cache.stats().missCount());
        write.executeWithoutResult(status->cache.invalidateAfterCommit(java.util.Set.of(fixture.pasta())));
        menus.dish(fixture.date(),fixture.pasta());assertEquals(misses+1,cache.stats().missCount());
    }
    @Test void realAdministrativeRollbackPreservesCacheMenuRevisionAndEvents() {
        var fixture=fixture();var before=menus.dish(fixture.date(),fixture.pasta());long events=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class);var write=new TransactionTemplate(manager);
        write.executeWithoutResult(status->{
            administration.update(actor(fixture),fixture.pasta(),changed(administration.get(fixture.pasta())));
            assertEquals(before.dishVersion(),cache.get(fixture.pasta(),1,()->{throw new AssertionError("Evicted before commit");}).dishVersion());status.setRollbackOnly();
        });assertEquals(before,menus.dish(fixture.date(),fixture.pasta()));assertEquals(1,jdbc.queryForObject("SELECT revision FROM daily_menus WHERE delivery_date=?",Long.class,fixture.date()));assertEquals(events,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class));
        administration.update(actor(fixture),fixture.pasta(),changed(administration.get(fixture.pasta())));
        var load=new AtomicInteger();cache.get(fixture.pasta(),1,()->{load.incrementAndGet();return dish(fixture.pasta(),1);});assertEquals(1,load.get());
        assertEquals(3000,menus.dish(fixture.date(),fixture.pasta()).basePriceCents());assertEquals(2,jdbc.queryForObject("SELECT revision FROM daily_menus WHERE delivery_date=?",Long.class,fixture.date()));assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events",Long.class)>events);
    }
    @Test void aRealProcessRestartReloadsTheSameDatabaseFactsFromAColdCache() throws Exception {
        var fixture=fixture();var reports=new java.util.ArrayList<String>();
        for(int attempt=0;attempt<2;attempt++) {
            var log=java.nio.file.Files.createTempFile("webox-cache-probe-",".log");
            try {
                String javaExecutable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString();
                String classpath=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
                var builder=new ProcessBuilder(javaExecutable,"-cp",classpath,CacheRestartProbe.class.getName(),fixture.date().toString(),fixture.pasta()).redirectErrorStream(true).redirectOutput(log.toFile());
                builder.environment().put("DB_URL",jdbcUrl());builder.environment().put("DB_USERNAME",MYSQL.getUsername());builder.environment().put("DB_PASSWORD",MYSQL.getPassword());builder.environment().put("MEDIA_ROOT",MEDIA_ROOT.toString());
                var child=builder.start();boolean done=child.waitFor(40,java.util.concurrent.TimeUnit.SECONDS);
                if(!done) child.destroyForcibly();assertTrue(done,"Cache probe timed out");assertEquals(0,child.exitValue(),"Cache probe failed; inspect test setup");
                reports.add(java.nio.file.Files.readAllLines(log).stream().filter(line->line.startsWith("CACHE_PROBE=")).findFirst().orElseThrow());
            } finally { java.nio.file.Files.deleteIfExists(log); }
        }
        assertEquals(reports.get(0),reports.get(1));assertEquals("CACHE_PROBE="+fixture.pasta()+":1:2650:20:0",reports.get(0));
    }
}
