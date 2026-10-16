package com.webox.support;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import com.webox.WeBoxApplication;
import com.webox.auth.UserIdentity;
import com.webox.shared.DomainTypes.Role;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
/** Only on the test classpath. Owns a disposable container and temporary media directory. */
public class E2eBackend extends MySqlTestSupport {
    public static void main(String[] args) {
        String key=System.getenv("WEBOX_E2E_FIXTURE_KEY");
        if(key==null || key.length()<32) throw new IllegalStateException("An ephemeral E2E fixture key is required.");
        URI origin=URI.create(System.getenv().getOrDefault("WEBOX_E2E_API_ORIGIN","http://127.0.0.1:18081"));
        int port=origin.getPort();
        if(!"http".equals(origin.getScheme()) || !Set.of("127.0.0.1","localhost").contains(origin.getHost()) || port<1024 || port>65535 || Set.of(3306,5173,8080).contains(port))
            throw new IllegalArgumentException("A dedicated loopback test port is required.");
        new SpringApplicationBuilder(WeBoxApplication.class,TimeConfiguration.class,Fixtures.class)
            .profiles("test","e2e").run("--server.address=127.0.0.1","--server.port="+port,
                "--spring.datasource.url="+jdbcUrl(),"--spring.datasource.username="+MYSQL.getUsername(),
                "--spring.datasource.password="+MYSQL.getPassword(),"--webox.media-root="+MEDIA_ROOT,
                "--server.servlet.session.cookie.secure=false","--webox.inventory.dispatch-enabled=true","--webox.rate-limit.enabled=true");
    }
    @TestConfiguration(proxyBeanMethods=false) @Import(FixtureController.class)
    static class Fixtures {
        @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter> isolatedRequestClock() {
            var filter=new org.springframework.web.filter.OncePerRequestFilter() {
                @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
                    String time=request.getHeader("X-WeBox-Fixture-Time");
                    if(time==null) {chain.doFilter(request,response);return;}
                    String supplied=request.getHeader("X-WeBox-Fixture-Key"),expected=System.getenv("WEBOX_E2E_FIXTURE_KEY");
                    if(supplied==null || expected==null || !java.security.MessageDigest.isEqual(supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8),expected.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {response.setStatus(403);return;}
                    java.time.Instant instant;try {instant=java.time.Instant.parse(time);}catch(java.time.format.DateTimeParseException e) {response.setStatus(400);return;}
                    try {TEST_CLOCK.requestTime(instant);chain.doFilter(request,response);}finally {TEST_CLOCK.requestTime(null);}
                }
            };
            var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.OncePerRequestFilter>(filter);registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);return registration;
        }
        @Bean FixtureFactory fixtureFactory(JdbcIndexedSessionRepository sessions, com.webox.auth.AuthService auth) { return new FixtureFactory(sessions,auth); }
        @Bean CatalogFixtures catalogFixtures(org.springframework.jdbc.core.JdbcTemplate jdbc,com.webox.media.MediaRepository media,java.nio.file.Path mediaRoot,JdbcIndexedSessionRepository sessions,com.webox.auth.AuthService auth,org.springframework.transaction.PlatformTransactionManager manager) { return new CatalogFixtures(jdbc,media,mediaRoot,sessions,auth,manager); }
        @Bean @Order(0) SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/__test/**").csrf(c->c.disable())
                .authorizeHttpRequests(c->c.anyRequest().permitAll()).build();
        }
    }
    @org.springframework.boot.test.context.TestComponent
    @RestController static class FixtureController {
        private final FixtureFactory fixtures;
        private final CatalogFixtures catalog;
        private final org.springframework.jdbc.core.JdbcTemplate jdbc;
        private final com.webox.recommendation.RecommendationObservations recommendations;
        private final java.util.Set<String> ownedDishes=java.util.concurrent.ConcurrentHashMap.newKeySet();
        FixtureController(FixtureFactory fixtures,CatalogFixtures catalog,org.springframework.jdbc.core.JdbcTemplate jdbc,com.webox.recommendation.RecommendationObservations recommendations) { this.fixtures=fixtures;this.catalog=catalog;this.jdbc=jdbc;this.recommendations=recommendations; }
        @GetMapping("/__test/recommendations/{requestId}") com.webox.recommendation.RecommendationObservations.Snapshot recommendation(@RequestHeader("X-WeBox-Fixture-Key") String key,@PathVariable String requestId) {checkKey(key);return recommendations.find(com.webox.shared.ValueRules.uuid(requestId)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
        @PostMapping("/__test/sessions") Map<String,Object> session(@RequestHeader("X-WeBox-Fixture-Key") String key,@RequestBody Map<String,String> body) {
            checkKey(key);
            var identity=fixtures.identity(Role.valueOf(body.getOrDefault("role","Employee")));
            return Map.of("user",identity.user(),"sessionId",identity.sessionId());
        }
        private void checkKey(String key) {
            String expected=System.getenv("WEBOX_E2E_FIXTURE_KEY");
            if(expected==null || !java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),key.getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        @PostMapping("/__test/reporting") ReportingFixtures.Fixture reporting(@RequestHeader("X-WeBox-Fixture-Key") String key) {
            checkKey(key);return new ReportingFixtures(jdbc,catalog,fixtures).create();
        }
        @PostMapping("/__test/catalog") CatalogFixtures.Menu catalog(@RequestHeader("X-WeBox-Fixture-Key") String key) {
            checkKey(key);var fixture=catalog.create();ownedDishes.add(fixture.pasta());return fixture;
        }
        @PostMapping("/__test/catalog/{dishId}") void change(@RequestHeader("X-WeBox-Fixture-Key") String key,@PathVariable String dishId,@RequestBody Map<String,String> body) {
            checkKey(key);if(!ownedDishes.contains(dishId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if("price".equals(body.get("change"))) jdbc.update("UPDATE dishes SET base_price_cents=3000,version=version+1 WHERE id=?",dishId);
            else if("one-cent-over-budget".equals(body.get("change"))) jdbc.update("UPDATE dishes SET base_price_cents=3001,version=version+1 WHERE id=?",dishId);
            else if("unlist".equals(body.get("change"))) jdbc.update("UPDATE dishes SET is_listed=FALSE,version=version+1 WHERE id=?",dishId);
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        @GetMapping("/api/v1/__fixture/identity") UserIdentity identity(Authentication auth) { return (UserIdentity)auth.getPrincipal(); }
    }
}
