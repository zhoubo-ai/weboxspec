package com.webox.shared;
import com.webox.support.*;
import com.webox.auth.SessionValidity;
import com.webox.shared.DomainTypes.Role;
import com.webox.inventory.OutboxWriter;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Import(HealthContractIT.Probes.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class HealthContractIT extends MySqlTestSupport {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired Flyway flyway;
    @Autowired FixtureFactory fixtures; @Autowired SessionValidity validity;
    @Autowired OutboxWriter outbox; @Autowired PlatformTransactionManager transactions;
    @Autowired JsonMapper json; @Autowired HikariDataSource dataSource;
    @Test void healthIsPublicAndOnlyReportsDatabaseReadiness() throws Exception {
        var result=mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        ContractAssertions.response("/health","get",200,result.getResponse().getContentAsString());
        assertEquals("+00:00",jdbc.queryForObject("SELECT @@session.time_zone",String.class));
    }
    @Test void realMigrationsAreRepeatableAndKeepOfficialSessionIndexes() {
        flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='SPRING_SESSION' AND INDEX_NAME IN ('SPRING_SESSION_IX1','SPRING_SESSION_IX2','SPRING_SESSION_IX3')",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND script IN ('V001__spring_session.sql','V002__outbox.sql')",Integer.class));
    }
    @Test void anonymousAndRoleFailuresHaveSafeContractErrors() throws Exception {
        error(get("/api/v1/_probe"),401,"UNAUTHENTICATED");
        error(get("/api/v1/admin/_probe").cookie(fixtures.identity(Role.Employee).cookie()),403,"FORBIDDEN");
        mvc.perform(get("/api/v1/admin/_probe").cookie(fixtures.identity(Role.Admin).cookie())).andExpect(status().isOk());
        error(get("/api/v1/not-a-route").cookie(fixtures.identity(Role.Employee).cookie()),404,"NOT_FOUND");
        error(post("/api/v1/auth/register").contentType("application/json").content("{}"),403,"CSRF_INVALID");
    }
    @Test void csrfIsSessionBoundAndCookieIsHttpOnlyLax() throws Exception {
        var result=mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        ContractAssertions.matches("Csrf",result.getResponse().getContentAsString());
        String cookie=result.getResponse().getHeader("Set-Cookie");
        assertNotNull(cookie);assertTrue(cookie.contains("HttpOnly"));assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.startsWith("WEBOXSESSION="));assertFalse(cookie.contains("Secure"));
    }
    @Test void strictJsonRejectsUnknownDuplicateAndCoercedFields() throws Exception {
        var identity=fixtures.identity(Role.Employee);
        String token=json.readTree(mvc.perform(get("/api/v1/auth/csrf").cookie(identity.cookie())).andReturn().getResponse().getContentAsString()).path("token").asText();
        for(String body:new String[]{"{\"message\":\"x\",\"count\":1,\"role\":\"Employee\",\"userId\":\"injected\"}","{\"message\":\"x\",\"count\":1.5,\"role\":\"Employee\"}","{\"message\":123,\"count\":1,\"role\":\"Employee\"}","{\"message\":\"x\",\"count\":\"1\",\"role\":\"Employee\"}","{\"message\":\"x\",\"count\":1,\"role\":0}","{\"message\":\"x\",\"message\":\"y\",\"count\":1,\"role\":\"Employee\"}","{}"}) {
            error(post("/api/v1/_probe").cookie(identity.cookie()).header("X-XSRF-TOKEN",token).contentType("application/json").content(body),400,"VALIDATION_ERROR");
        }
        mvc.perform(post("/api/v1/_probe").cookie(identity.cookie()).header("X-XSRF-TOKEN",token).contentType("application/json").content("{\"message\":\"x\",\"count\":1,\"role\":\"Employee\"}")).andExpect(status().isOk());
        error(post("/api/v1/_probe").cookie(identity.cookie()).header("X-XSRF-TOKEN",token).contentType("application/json").content(" ".repeat(WebConfiguration.MAX_JSON_BYTES+1)),400,"VALIDATION_ERROR");
    }
    @Test void unexpectedErrorsDoNotExposePrivateContent() throws Exception {
        var result=error(get("/api/v1/_probe/failure").cookie(fixtures.identity(Role.Employee).cookie()),500,"INTERNAL_ERROR");
        assertFalse(result.contains("private-fixture-secret"));assertFalse(result.contains("Exception"));
    }
    @Test void outboxRequiresTransactionAndRollsBackWithBusinessChanges() {
        LocalDate date=LocalDate.of(2097,7,13);int before=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,date.toString());
        int pendingBefore=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=? AND published_at IS NULL",Integer.class,date.toString());
        assertThrows(IllegalStateException.class,()->outbox.menuChanged(date,1));
        new TransactionTemplate(transactions).executeWithoutResult(status->{outbox.menuChanged(date,1);status.setRollbackOnly();});
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,date.toString()));
        new TransactionTemplate(transactions).executeWithoutResult(status->outbox.menuChanged(date,1));
        assertEquals(before+1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=?",Integer.class,date.toString()));
        assertEquals(pendingBefore+1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=? AND published_at IS NULL",Integer.class,date.toString()));
    }
    @Test void streamSessionChecksDoNotRefreshIdleTime() {
        var identity=fixtures.identity(Role.Employee);
        long before=jdbc.queryForObject("SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID=?",Long.class,identity.sessionId());
        assertTrue(validity.active(identity.sessionId()));assertTrue(validity.active(identity.sessionId()));
        assertEquals(before,jdbc.queryForObject("SELECT LAST_ACCESS_TIME FROM SPRING_SESSION WHERE SESSION_ID=?",Long.class,identity.sessionId()));
        jdbc.update("UPDATE SPRING_SESSION SET EXPIRY_TIME=0,LAST_ACCESS_TIME=0 WHERE SESSION_ID=?",identity.sessionId());
        assertFalse(validity.active(identity.sessionId()));
    }
    @Test @Order(Integer.MAX_VALUE) void unavailableConnectionPoolReturnsSafe503() throws Exception {
        dataSource.close();
        var result=error(get("/api/v1/health"),503,"TEMPORARY_UNAVAILABLE");
        assertTrue(json.readTree(result).path("retryable").asBoolean());
    }
    private String error(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,int status,String code) throws Exception {
        String body=mvc.perform(request).andExpect(status().is(status)).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        ContractAssertions.matches("Error",body);assertEquals(code,json.readTree(body).path("code").asText());return body;
    }
    @TestConfiguration(proxyBeanMethods=false) @Import(ProbeController.class)
    static class Probes { @Bean FixtureFactory fixtures(JdbcIndexedSessionRepository sessions, com.webox.auth.AuthService auth) { return new FixtureFactory(sessions,auth); } }
    @org.springframework.boot.test.context.TestComponent
    @RestController static class ProbeController {
        @GetMapping({"/api/v1/_probe","/api/v1/admin/_probe"}) Map<String,String> get() { return Map.of("status","ready"); }
        @PostMapping("/api/v1/_probe") Probe post(@Valid @RequestBody Probe body) { return body; }
        @GetMapping("/api/v1/_probe/failure") void failure() { throw new IllegalStateException("private-fixture-secret"); }
    }
    record Probe(@NotNull String message,@NotNull Integer count,@NotNull Role role) {}
}
