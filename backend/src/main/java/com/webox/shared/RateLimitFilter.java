package com.webox.shared;
import com.github.benmanes.caffeine.cache.*;
import com.webox.auth.UserIdentity;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
/** Single-instance token buckets, after authentication/CSRF. Monotonic time; bounded identity storage. */
@Component @ConditionalOnWebApplication
public class RateLimitFilter extends OncePerRequestFilter {
    private record Policy(int capacity,int perMinute){Policy{if(capacity<1||perMinute<1||capacity>1000000||perMinute>1000000)throw new IllegalArgumentException("Invalid rate limit.");}}
    private static final class Bucket {
        private double tokens;private long updated=System.nanoTime();private final Policy policy;
        Bucket(Policy policy){this.policy=policy;tokens=policy.capacity();}
        synchronized int take(){long now=System.nanoTime();tokens=Math.min(policy.capacity(),tokens+(now-updated)*policy.perMinute()/60_000_000_000.0);updated=now;if(tokens>=1){tokens--;return 0;}return Math.max(1,(int)Math.ceil((1-tokens)*60/policy.perMinute()));}
    }
    private final Cache<String,Bucket> buckets=Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofMinutes(30)).build();
    private final JsonMapper json;private final OperationalMetrics metrics;private final boolean enabled;private final Policy auth,write,ai;private final Bucket globalAuth;
    public RateLimitFilter(JsonMapper json,OperationalMetrics metrics,
        @Value("${webox.rate-limit.enabled:true}") boolean enabled,
        @Value("${webox.rate-limit.auth-capacity:60}") int authCapacity,@Value("${webox.rate-limit.auth-per-minute:120}") int authRate,
        @Value("${webox.rate-limit.write-capacity:120}") int writeCapacity,@Value("${webox.rate-limit.write-per-minute:300}") int writeRate,
        @Value("${webox.rate-limit.ai-capacity:10}") int aiCapacity,@Value("${webox.rate-limit.ai-per-minute:30}") int aiRate,
        @Value("${webox.rate-limit.global-auth-capacity:600}") int globalCapacity,@Value("${webox.rate-limit.global-auth-per-minute:1200}") int globalRate) {
        this.json=json;this.metrics=metrics;this.enabled=enabled;auth=new Policy(authCapacity,authRate);write=new Policy(writeCapacity,writeRate);ai=new Policy(aiCapacity,aiRate);globalAuth=new Bucket(new Policy(globalCapacity,globalRate));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(!enabled){chain.doFilter(request,response);return;}String path=request.getRequestURI().substring(request.getContextPath().length()),scope=null,key=null;Policy policy=null;
        if(Set.of("/api/v1/auth/csrf","/api/v1/auth/login","/api/v1/auth/register").contains(path)){scope="authentication";key="auth:"+request.getRemoteAddr();policy=auth;}
        else if(!Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && path.startsWith("/api/v1/") && !path.equals("/api/v1/auth/logout")) {
            var authentication=SecurityContextHolder.getContext().getAuthentication();if(authentication!=null && authentication.getPrincipal() instanceof UserIdentity user){boolean recommendation=path.equals("/api/v1/recommendations/stream");scope=recommendation?"recommendation":"write";key=scope+":"+user.userId();policy=recommendation?ai:write;}
        }
        if(key!=null){Policy selected=policy;int retry=buckets.get(key,ignored->new Bucket(selected)).take();if(retry==0&&scope.equals("authentication"))retry=globalAuth.take();if(retry>0){metrics.limited(scope);response.setHeader("Retry-After",String.valueOf(retry));ApiErrors.write(json,request,response,new ApiException(429,"RATE_LIMITED","Too many requests. Please try again shortly.",true,Map.of()));return;}}
        chain.doFilter(request,response);
    }
}
