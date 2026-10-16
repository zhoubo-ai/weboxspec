package com.webox.shared;
import jakarta.annotation.*;
import java.io.IOException;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
@Component @ConditionalOnWebApplication
public class MaintenanceJobs {
    private final JdbcTemplate jdbc;private final BusinessClock clock;private final Path media;private final TransactionTemplate writes;private final boolean enabled;private final long delay,interval;private final int batch;private final Duration quotes,events,files;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"webox-maintenance");thread.setDaemon(true);return thread;});private String fileCursor="";
    public MaintenanceJobs(JdbcTemplate jdbc,BusinessClock clock,Path mediaRoot,PlatformTransactionManager manager,
        @Value("${webox.maintenance.enabled:true}") boolean enabled,@Value("${webox.maintenance.initial-delay-ms:60000}") long delay,@Value("${webox.maintenance.interval-ms:3600000}") long interval,
        @Value("${webox.maintenance.batch-size:100}") int batch,@Value("${webox.maintenance.quote-retention-hours:24}") int quoteHours,@Value("${webox.maintenance.outbox-retention-days:7}") int eventDays,@Value("${webox.maintenance.media-retention-hours:24}") int fileHours) {
        if(delay<0||interval<100||batch<1||batch>1000||quoteHours<1||eventDays<1||fileHours<1)throw new IllegalArgumentException("Invalid maintenance settings.");
        this.jdbc=jdbc;this.clock=clock;media=mediaRoot;this.enabled=enabled;this.delay=delay;this.interval=interval;this.batch=batch;quotes=Duration.ofHours(quoteHours);events=Duration.ofDays(eventDays);files=Duration.ofHours(fileHours);writes=new TransactionTemplate(manager);writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);writes.setTimeout(5);
    }
    @PostConstruct void start(){if(enabled)worker.scheduleWithFixedDelay(()->{try{run();}catch(RuntimeException failure){LoggerFactory.getLogger(MaintenanceJobs.class).warn("Maintenance deferred: type={}",failure.getClass().getSimpleName());}},delay,interval,TimeUnit.MILLISECONDS);}
    @PreDestroy void stop(){worker.shutdownNow();}
    public synchronized void run() {
        var now=clock.now();writes.executeWithoutResult(status->{
            // Lock parents before children, matching checkout. Skip in-flight work; never touch consumed quotes.
            var ids=jdbc.queryForList("SELECT id FROM checkout_quotes WHERE expires_at<? AND consumed_order_id IS NULL ORDER BY expires_at,id LIMIT "+batch+" FOR UPDATE SKIP LOCKED",String.class,Timestamp.from(now.minus(quotes)));
            for(String id:ids){jdbc.update("DELETE FROM checkout_quote_items WHERE quote_id=?",id);jdbc.update("DELETE FROM checkout_quotes WHERE id=? AND consumed_order_id IS NULL",id);}
            jdbc.update("DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at<? ORDER BY published_at,id LIMIT "+batch,Timestamp.from(now.minus(events)));
        });
        // Keep all registered assets, including replaced images. Only old unregistered crash remnants are removed.
        // A bounded sorted window advances across runs, avoiding starvation without retaining a whole directory listing.
        var window=new TreeSet<Path>(Comparator.comparing(p->p.getFileName().toString()));
        try(var directory=Files.newDirectoryStream(media)) {
            for(Path path:directory){String name=path.getFileName().toString();if(name.compareTo(fileCursor)<=0 || !name.matches("(?:upload-[A-Za-z0-9-]+\\.tmp|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:png|jpeg))"))continue;window.add(path);if(window.size()>1000)window.pollLast();}
            if(window.isEmpty()){fileCursor="";return;}
            for(Path path:window){String name=path.getFileName().toString();if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || !Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(now.minus(files)))continue;
                if(jdbc.queryForObject("SELECT COUNT(*) FROM media_assets WHERE storage_key=?",Integer.class,name)==0)Files.deleteIfExists(path);
            }fileCursor=window.last().getFileName().toString();
        }catch(IOException failure){throw new IllegalStateException("Media maintenance could not complete.");}
    }
}
