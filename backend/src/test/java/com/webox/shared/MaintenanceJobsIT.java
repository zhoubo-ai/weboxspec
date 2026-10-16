package com.webox.shared;
import com.webox.support.AdminTestSupport;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"webox.maintenance.enabled=true","webox.maintenance.initial-delay-ms=100","webox.maintenance.interval-ms=100","webox.inventory.dispatch-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class MaintenanceJobsIT extends AdminTestSupport {
    @Test void scheduledCleanupKeepsOrdersKeysConsumedQuotesPendingEventsAndReferencedImages()throws Exception {
        var user=browser();var other=browser();var menu=menu();String old=quote(user,menu,1),recent=quote(user,menu,1),live=quote(other,menu,1),consumed=quote(user,menu,1),key=UUID.randomUUID().toString();var body=Map.of("quoteId",consumed,"addressText","Maintenance office");var order=postJson("/orders",user,body,key);assertEquals(201,order.getStatus());String orderId=field(order,"orderId");Instant now=TEST_CLOCK.instant();
        for(String id:List.of(old,consumed))jdbc.update("UPDATE checkout_quotes SET created_at=?,expires_at=? WHERE id=?",Timestamp.from(now.minus(Duration.ofDays(2)).minusSeconds(300)),Timestamp.from(now.minus(Duration.ofDays(2))),id);
        jdbc.update("UPDATE checkout_quotes SET created_at=?,expires_at=? WHERE id=?",Timestamp.from(now.minusSeconds(900)),Timestamp.from(now.minusSeconds(600)),recent);
        String published=UUID.randomUUID().toString(),pending=UUID.randomUUID().toString(),fresh=UUID.randomUUID().toString();for(String id:List.of(published,pending,fresh)){Instant time=id.equals(fresh)?now:now.minus(Duration.ofDays(8));jdbc.update("INSERT INTO outbox_events(id,event_type,aggregate_key,payload,created_at,published_at,next_attempt_at) VALUES (?,'inventory.updated','maintenance-test',JSON_OBJECT(),?,?,?)",id,Timestamp.from(time),id.equals(pending)?null:Timestamp.from(time),Timestamp.from(time));}
        Path temporary=Files.createTempFile(MEDIA_ROOT,"upload-",".tmp"),freshTemporary=Files.createTempFile(MEDIA_ROOT,"upload-",".tmp"),orphan=MEDIA_ROOT.resolve(UUID.randomUUID()+".png"),symlink=MEDIA_ROOT.resolve("upload-"+UUID.randomUUID()+".tmp");Files.writeString(orphan,"unreferenced crash remnant");Path referenced=MEDIA_ROOT.resolve(jdbc.queryForObject("SELECT storage_key FROM media_assets WHERE id=?",String.class,menu.mediaId()));FileTime oldTime=FileTime.from(now.minus(Duration.ofDays(2)));for(Path path:List.of(temporary,orphan,referenced))Files.setLastModifiedTime(path,oldTime);Files.setLastModifiedTime(freshTemporary,FileTime.from(now));Files.createSymbolicLink(symlink,referenced.getFileName());
        try {
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();while(System.nanoTime()<deadline&&(existsQuote(old)||Files.exists(temporary)||Files.exists(orphan)||existsEvent(published)))Thread.sleep(25);
        assertFalse(existsQuote(old),"Expired unconsumed quote must be cleaned by the scheduled job");assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM checkout_quote_items WHERE quote_id=?",Integer.class,old));assertFalse(existsEvent(published));assertFalse(Files.exists(temporary));assertFalse(Files.exists(orphan));
        for(String id:List.of(recent,live,consumed))assertTrue(existsQuote(id));for(String id:List.of(pending,fresh))assertTrue(existsEvent(id));assertTrue(Files.exists(referenced));assertTrue(Files.exists(freshTemporary));assertTrue(Files.isSymbolicLink(symlink));assertEquals(200,read("/orders/"+orderId,user).getStatus());assertEquals(200,postJson("/orders",user,body,key).getStatus());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=? AND idempotency_key=?",Integer.class,user.identity().user().userId(),key));Files.deleteIfExists(symlink);
        } finally {for(Path path:List.of(temporary,freshTemporary,orphan,symlink))Files.deleteIfExists(path);}
    }
    private boolean existsQuote(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM checkout_quotes WHERE id=?",Integer.class,id)>0;}
    private boolean existsEvent(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE id=?",Integer.class,id)>0;}
}
