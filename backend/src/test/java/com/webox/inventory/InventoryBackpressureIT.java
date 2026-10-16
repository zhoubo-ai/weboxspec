package com.webox.inventory;
import com.webox.auth.SessionValidity;
import com.webox.support.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class InventoryBackpressureIT extends AdminTestSupport {
    @Autowired SessionValidity validity;@Autowired InventoryQuery inventory;
    static class Transport extends ServletOutputStream {
        final boolean ready;final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Transport(boolean ready){this.ready=ready;}
        @Override public boolean isReady(){return ready;}
        @Override public void setWriteListener(WriteListener listener){if(ready)try{listener.onWritePossible();}catch(IOException e){throw new RuntimeException(e);}}
        @Override public synchronized void write(int b){if(!ready)throw new AssertionError("A non-ready socket must never be written.");bytes.write(b);}
        synchronized String text(){return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);}
    }
    private AsyncContext begin(InventorySubscriptions.Connection connection,java.time.LocalDate date,Transport output) throws Exception {
        var async=mock(AsyncContext.class);var response=mock(HttpServletResponse.class);when(response.getOutputStream()).thenReturn(output);connection.begin(inventory.snapshot(date),async,response);return async;
    }
    @Test void aNonReadyConnectionIsEvictedWhileAnotherSubscriberAndRealOrdersContinue() throws Exception {
        var user=browser();var slowMenu=menu();var fastMenu=menu();var hub=new InventorySubscriptions(validity,json,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),15000,1800000,3,4,4,5000);
        try {
            var slow=begin(hub.register(slowMenu.date(),user.identity().sessionId()),slowMenu.date(),new Transport(false));var output=new Transport(true);begin(hub.register(fastMenu.date(),user.identity().sessionId()),fastMenu.date(),output);
            long start=System.nanoTime();for(int i=0;i<10;i++)hub.publish(slowMenu.date(),"menu.changed",json.writeValueAsString(Map.of("deliveryDate",slowMenu.date().toString(),"menuRevision",i+1)));assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));verify(slow,times(1)).complete();assertEquals(1,hub.size());
            var order=postJson("/orders",user,Map.of("quoteId",quote(user,fastMenu,1),"addressText","Backpressure office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());hub.publish(fastMenu.date(),"menu.changed",json.writeValueAsString(Map.of("deliveryDate",fastMenu.date().toString(),"menuRevision",1)));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!output.text().contains("event: menu.changed") && System.nanoTime()<deadline)Thread.sleep(10);assertTrue(output.text().contains("event: inventory.snapshot"));assertTrue(output.text().contains("event: menu.changed"));assertEquals(19,jdbc.queryForObject("SELECT total_quantity-allocated_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,fastMenu.date(),fastMenu.pasta()));
        }finally{hub.stop();}
    }
    @Test void stalledWritesAndGlobalCapacityAreBounded() throws Exception {
        var user=browser();var menu=menu();var hub=new InventorySubscriptions(validity,json,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),15000,1800000,3,2,256,100);hub.start();
        try {
            var first=begin(hub.register(menu.date(),user.identity().sessionId()),menu.date(),new Transport(false));var second=begin(hub.register(menu.date(),user.identity().sessionId()),menu.date(),new Transport(false));var exception=assertThrows(com.webox.shared.ApiException.class,()->hub.register(menu.date(),UUID.randomUUID().toString()));assertEquals(429,exception.status());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(hub.size()>0 && System.nanoTime()<deadline)Thread.sleep(10);assertEquals(0,hub.size());verify(first,times(1)).complete();verify(second,times(1)).complete();
        }finally{hub.stop();}
    }
    @Test void reconnectAtCapacityProbesClosedSocketsWithoutEvictingAHealthySession() throws Exception {
        var user=browser();var other=browser();var menu=menu();var hub=new InventorySubscriptions(validity,json,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),15000,1800000,1,3,256,5000);
        var disconnected=new java.util.concurrent.atomic.AtomicBoolean();
        var stale=new Transport(true){@Override public synchronized void write(int value){if(disconnected.get())throw new IllegalStateException("Controlled closed transport");super.write(value);}};
        try {
            var closed=begin(hub.register(menu.date(),user.identity().sessionId()),menu.date(),stale);var healthy=new Transport(true);begin(hub.register(menu.date(),other.identity().sessionId()),menu.date(),healthy);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(!stale.text().contains("inventory.snapshot") && System.nanoTime()<deadline)Thread.sleep(10);assertTrue(stale.text().contains("inventory.snapshot"));disconnected.set(true);
            assertEquals(429,assertThrows(com.webox.shared.ApiException.class,()->hub.register(menu.date(),user.identity().sessionId())).status());
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(hub.size()>1 && System.nanoTime()<deadline)Thread.sleep(10);assertEquals(1,hub.size());verify(closed,times(1)).complete();
            begin(hub.register(menu.date(),user.identity().sessionId()),menu.date(),new Transport(true));assertEquals(2,hub.size());assertTrue(healthy.text().contains("inventory.snapshot"));
        }finally{hub.stop();}
    }
}
