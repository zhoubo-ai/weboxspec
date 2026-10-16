package com.webox.inventory;
import com.webox.auth.SessionValidity;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
class InventoryAuthorizationTest {
 @org.junit.jupiter.api.Test void anEventArrivingDuringAnEmptyQueueFlushRechecksRevocation() throws Exception {
  var authorized=new AtomicBoolean(true);var validity=mock(SessionValidity.class);
  when(validity.active("session")).thenAnswer(call->authorized.get());
  var hub=new InventorySubscriptions(validity,JsonMapper.builder().build(),new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),15000,1800000,3,4,256,5000);
  var date=LocalDate.of(2032,1,1);var paused=new CountDownLatch(1);var injected=new CountDownLatch(1);
  class Transport extends ServletOutputStream {
   final ByteArrayOutputStream bytes=new ByteArrayOutputStream();volatile boolean ready=true,inject;int notReady,resumedReads,writes;WriteListener listener;
   public boolean isReady(){if(!ready){if(++notReady==3)paused.countDown();return false;}if(inject && ++resumedReads==2){authorized.set(false);hub.publish(date,"inventory.updated","secret-after-revocation");injected.countDown();}return true;}
   public void setWriteListener(WriteListener listener){this.listener=listener;}
   public synchronized void write(int b){bytes.write(b);}
   public synchronized void write(byte[] b){bytes.writeBytes(b);if(++writes==1)ready=false;}
   synchronized String text(){return bytes.toString(java.nio.charset.StandardCharsets.UTF_8);}
  }
  var output=new Transport();var async=mock(AsyncContext.class);var response=mock(HttpServletResponse.class);when(response.getOutputStream()).thenReturn(output);
  try {
   hub.register(date,"session").begin(new InventoryModels.Snapshot(date,0,List.of(),Instant.EPOCH),async,response);
   if(!paused.await(3,TimeUnit.SECONDS))throw new AssertionError("Initial snapshot did not reach controlled backpressure.");
   output.inject=true;output.ready=true;output.listener.onWritePossible();
   if(!injected.await(3,TimeUnit.SECONDS))throw new AssertionError("Controlled interleaving was not reached.");
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
   while(!output.text().contains("session.expired") && !output.text().contains("secret-after-revocation") && System.nanoTime()<deadline)Thread.sleep(5);
   boolean leaked=output.text().contains("secret-after-revocation");boolean expired=output.text().contains("session.expired");
   System.out.println("controlledInterleaving=true unauthorizedFrame="+leaked+" expirationFrame="+expired);
   if(leaked || !expired)throw new AssertionError("Every newly queued data write requires a fresh session check.");
  }finally{hub.stop();}
 }
}
