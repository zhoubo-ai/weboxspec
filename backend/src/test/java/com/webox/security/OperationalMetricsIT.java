package com.webox.security;
import com.webox.support.AdminTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class OperationalMetricsIT extends AdminTestSupport {
    @Autowired MeterRegistry metrics;
    @Test void committedOrdersAndReplaysAreCountedSeparatelyWithoutPrivateLabels()throws Exception {
        var user=browser();var menu=menu();String quote=quote(user,menu,1),key=UUID.randomUUID().toString();double created=metrics.get("webox.orders").tag("outcome","created").counter().count(),replays=metrics.get("webox.orders").tag("outcome","replayed").counter().count();var body=Map.of("quoteId",quote,"addressText","Private metrics fixture office");assertEquals(201,postJson("/orders",user,body,key).getStatus());assertEquals(200,postJson("/orders",user,body,key).getStatus());assertEquals(created+1,metrics.get("webox.orders").tag("outcome","created").counter().count());assertEquals(replays+1,metrics.get("webox.orders").tag("outcome","replayed").counter().count());
        error(postJson("/orders",user,Map.of("quoteId",quote,"addressText","Different address"),key),409,"IDEMPOTENCY_KEY_REUSED");assertTrue(metrics.get("webox.conflicts").tag("code","IDEMPOTENCY_KEY_REUSED").counter().count()>=1);assertTrue(metrics.get("webox.outbox.pending").gauge().value()>=0);assertEquals(0,metrics.get("webox.inventory.active").gauge().value());assertEquals(0,metrics.get("webox.ai.active").gauge().value());assertNotNull(metrics.find("cache.gets").tag("cache","menuDishDetails").functionCounter());
        for(var meter:metrics.getMeters())if(meter.getId().getName().startsWith("webox."))for(var tag:meter.getId().getTags()){assertTrue(Set.of("outcome","code","scope").contains(tag.getKey()));assertFalse(tag.getValue().contains(user.identity().user().userId()));assertFalse(tag.getValue().contains("Private"));}
    }
}
