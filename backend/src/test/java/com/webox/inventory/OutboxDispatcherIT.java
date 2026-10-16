package com.webox.inventory;
import com.webox.support.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class OutboxDispatcherIT extends AdminTestSupport {
    @MockitoSpyBean OutboxDispatcher dispatcher;@MockitoSpyBean InventorySubscriptions subscriptions;
    @Autowired InventoryQuery inventory;
    private void insert(String id,LocalDate date,int attempts) {
        var now=Timestamp.from(TEST_CLOCK.instant());jdbc.update("INSERT INTO outbox_events(id,event_type,aggregate_key,payload,created_at,next_attempt_at,attempt_count) VALUES (?,'menu.changed',?,?,?,?,?)",id,date.toString(),json.writeValueAsString(Map.of("deliveryDate",date.toString(),"menuRevision",1)),now,now,attempts);
    }
    private boolean published(String id){return jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events WHERE id=?",Boolean.class,id);}
    @Test void aSmallIdCommittedAfterALargerIdIsStillPublishedWithoutSubscribers() throws Exception {
        LocalDate date=menu().date();String low="00000000-"+UUID.randomUUID().toString().substring(9),high="ffffffff-"+UUID.randomUUID().toString().substring(9);var inserted=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try {
            var delayed=executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(status->{insert(low,date,0);inserted.countDown();try{assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}}));assertTrue(inserted.await(3,TimeUnit.SECONDS));insert(high,date,0);dispatcher.dispatch();assertTrue(published(high));release.countDown();delayed.get(5,TimeUnit.SECONDS);dispatcher.dispatch();assertTrue(published(low));assertEquals(0,subscriptions.size());
        }finally{release.countDown();executor.shutdownNow();}
    }
    @Test void failureBeforeBroadcastRetainsTheRecordAndBacksOffThenRecovers() {
        LocalDate date=menu().date();String id=UUID.randomUUID().toString();insert(id,date,0);doThrow(new IllegalStateException("Controlled pre-broadcast failure")).doCallRealMethod().when(subscriptions).publish(eq(date),eq("menu.changed"),anyString());
        dispatcher.dispatch();assertFalse(published(id));assertEquals(1,jdbc.queryForObject("SELECT attempt_count FROM outbox_events WHERE id=?",Integer.class,id));dispatcher.dispatch();verify(subscriptions,times(1)).publish(eq(date),eq("menu.changed"),anyString());TEST_CLOCK.set(TEST_CLOCK.instant().plusMillis(250));dispatcher.dispatch();assertTrue(published(id));
    }
    @Test void failureAfterBroadcastBeforeAcknowledgementReplaysTheSameDurableEvent() {
        LocalDate date=menu().date();String id=UUID.randomUUID().toString();insert(id,date,0);doThrow(new IllegalStateException("Controlled post-broadcast failure")).doCallRealMethod().when(dispatcher).markPublished(id);
        dispatcher.dispatch();assertFalse(published(id));TEST_CLOCK.set(TEST_CLOCK.instant().plusMillis(250));dispatcher.dispatch();assertTrue(published(id));var payload=org.mockito.ArgumentCaptor.forClass(String.class);verify(subscriptions,times(2)).publish(eq(date),eq("menu.changed"),payload.capture());assertEquals(payload.getAllValues().get(0),payload.getAllValues().get(1));assertEquals(1,jdbc.queryForObject("SELECT attempt_count FROM outbox_events WHERE id=?",Integer.class,id));
    }
    @Test void rollbackIsNeverVisibleToTheDispatcherAndFailureNeverDeletesTheRow() {
        LocalDate date=menu().date();String id=UUID.randomUUID().toString();new TransactionTemplate(manager).executeWithoutResult(status->{insert(id,date,0);status.setRollbackOnly();});dispatcher.dispatch();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE id=?",Integer.class,id));verify(subscriptions,never()).publish(eq(date),anyString(),anyString());
    }
}
