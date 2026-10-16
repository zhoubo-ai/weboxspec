package com.webox.support;
import com.webox.WeBoxApplication;
import com.webox.catalog.MenuDetailsCache;
import com.webox.catalog.MenuService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
/** Launched twice as separate JVMs; credentials are inherited through environment variables. */
public final class CacheRestartProbe {
    public static void main(String[] args) {
        try(var context=new SpringApplicationBuilder(WeBoxApplication.class).web(WebApplicationType.NONE).profiles("test").run()) {
            var cache=context.getBean(MenuDetailsCache.class);var service=context.getBean(MenuService.class);
            if(cache.size()!=0 || cache.stats().missCount()!=0) throw new AssertionError("Process must start with a cold cache");
            var first=service.dish(java.time.LocalDate.parse(args[0]),args[1]);var second=service.dish(java.time.LocalDate.parse(args[0]),args[1]);
            if(!first.equals(second) || cache.stats().missCount()!=1 || cache.stats().hitCount()!=1) throw new AssertionError("Cold/warm reads must agree");
            System.out.println("CACHE_PROBE="+first.dishId()+":"+first.dishVersion()+":"+first.basePriceCents()+":"+first.remaining()+":"+first.inventoryVersion());
        }
    }
}
