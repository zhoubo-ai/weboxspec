package com.webox.security;
import com.webox.support.AdminTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest(properties={"webox.rate-limit.enabled=true","webox.rate-limit.auth-capacity=2","webox.rate-limit.auth-per-minute=1","webox.rate-limit.write-capacity=2","webox.rate-limit.write-per-minute=1","webox.rate-limit.ai-capacity=1","webox.rate-limit.ai-per-minute=1"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class RateLimitIT extends AdminTestSupport {
    @Test void authenticationBurstHasRetryAfterAndDoesNotTrustClientForwardedHeaders()throws Exception {
        for(int i=0;i<2;i++)assertEquals(200,mvc.perform(get("/api/v1/auth/csrf").with(r->{r.setRemoteAddr("192.0.2.8");return r;})).andReturn().getResponse().getStatus());
        var response=mvc.perform(get("/api/v1/auth/csrf").header("X-Forwarded-For","192.0.2.99").with(r->{r.setRemoteAddr("192.0.2.8");return r;})).andReturn().getResponse();error(response,429,"RATE_LIMITED");assertTrue(Integer.parseInt(response.getHeader("Retry-After"))>=1);assertNotNull(response.getHeader("X-Request-Id"));assertEquals(200,mvc.perform(get("/api/v1/health").with(r->{r.setRemoteAddr("192.0.2.8");return r;})).andReturn().getResponse().getStatus());
    }
    @Test void authenticatedWriteAndAiLimitsArePerAccountAndPreserveCsrfRejection()throws Exception {
        var one=browser();var two=browser();for(int i=0;i<2;i++)error(write("PUT","/me/preferences",one,java.util.Map.of()),400,"VALIDATION_ERROR");error(write("PUT","/me/preferences",one,java.util.Map.of()),429,"RATE_LIMITED");error(write("PUT","/me/preferences",two,java.util.Map.of()),400,"VALIDATION_ERROR");
        error(mvc.perform(put("/api/v1/me/preferences").cookie(one.identity().cookie()).contentType("application/json").content("{}")).andReturn().getResponse(),403,"CSRF_INVALID");
        error(postJson("/recommendations/stream",two,java.util.Map.of(),null),400,"VALIDATION_ERROR");error(postJson("/recommendations/stream",two,java.util.Map.of(),null),429,"RATE_LIMITED");
    }
}
