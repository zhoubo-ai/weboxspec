package com.webox.catalog;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import com.github.benmanes.caffeine.cache.*;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
@Component
public class MenuDetailsCache {
    private record Key(String id,long version) {}
    private final Cache<Key,MenuModels.Details> cache;
    @Autowired public MenuDetailsCache(MeterRegistry metrics) {
        this(Duration.ofMinutes(5),10000,Ticker.systemTicker());CaffeineCacheMetrics.monitor(metrics,cache,"menuDishDetails");
    }
    public MenuDetailsCache(Duration expiration,int capacity,Ticker ticker) {
        cache=Caffeine.newBuilder().maximumSize(capacity).expireAfterWrite(expiration).ticker(ticker).recordStats().build();
    }
    public MenuModels.Details get(String id,long version,Supplier<MenuModels.Details> loader) {
        return cache.get(new Key(id,version),key->{var loaded=loader.get();if(!key.id().equals(loaded.dishId()) || key.version()!=loaded.dishVersion()) throw new IllegalStateException("Mismatched menu cache version.");return loaded;});
    }
    public void invalidateAfterCommit(Set<String> ids) {
        Set<String> snapshot=Set.copyOf(ids);
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Cache invalidation requires a business transaction.");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.asMap().keySet().removeIf(key->snapshot.contains(key.id())); }
        });
    }
    public long size() { return cache.estimatedSize(); }
    public void cleanUp() { cache.cleanUp(); }
    public CacheStats stats() { return cache.stats(); }
}
