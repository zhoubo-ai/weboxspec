package com.webox.auth;
import com.webox.support.MySqlTestSupport;
import com.webox.support.ContractAssertions;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AuthContractIT extends MySqlTestSupport {
    @Autowired com.webox.shared.AdminInitializer admins; @Autowired MockMvc mvc; @Autowired JsonMapper json; @Autowired JdbcTemplate jdbc;
    record Browser(Cookie cookie,String token) {}
    Browser csrf(Cookie cookie) throws Exception {
        var req=get("/api/v1/auth/csrf");if(cookie!=null) req.cookie(cookie);
        var result=mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse();
        ContractAssertions.matches("Csrf",result.getContentAsString());
        return new Browser(cookie==null?result.getCookie("WEBOXSESSION"):cookie,json.readTree(result.getContentAsString()).path("token").asText());
    }
    org.springframework.mock.web.MockHttpServletResponse postJson(String path,Browser browser,Object body,int expected) throws Exception {
        var response=mvc.perform(post("/api/v1"+path).cookie(browser.cookie()).header("X-XSRF-TOKEN",browser.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().is(expected)).andReturn().getResponse();
        if(expected!=204) ContractAssertions.response(path,"post",expected,response.getContentAsString());return response;
    }
    @Test void registrationDoesNotLoginAndLoginRotatesSessionAndCsrf() throws Exception {
        var anonymous=csrf(null);String email="  USER"+UUID.randomUUID()+"@Example.org  ";String password="  Ab1234🍜  ";
        var registration=postJson("/auth/register",anonymous,Map.of("email",email,"password",password),201);
        assertEquals("Employee",json.readTree(registration.getContentAsString()).path("role").asText());
        mvc.perform(get("/api/v1/auth/me").cookie(anonymous.cookie())).andExpect(status().isUnauthorized());
        var login=postJson("/auth/login",anonymous,Map.of("email",email.trim().toLowerCase(java.util.Locale.ROOT),"password",password),200);
        var authenticated=login.getCookie("WEBOXSESSION");assertNotNull(authenticated);assertNotEquals(anonymous.cookie().getValue(),authenticated.getValue());
        mvc.perform(get("/api/v1/auth/me").cookie(anonymous.cookie())).andExpect(status().isUnauthorized());
        var me=mvc.perform(get("/api/v1/auth/me").cookie(authenticated)).andExpect(status().isOk()).andReturn().getResponse();
        ContractAssertions.matches("User",me.getContentAsString());assertEquals(json.readTree(registration.getContentAsString()),json.readTree(me.getContentAsString()));
        postJson("/auth/logout",new Browser(authenticated,anonymous.token()),Map.of(),403);
        var fresh=csrf(authenticated);postJson("/auth/logout",fresh,Map.of(),204);
        mvc.perform(get("/api/v1/auth/me").cookie(authenticated)).andExpect(status().isUnauthorized());
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,json.readTree(me.getContentAsString()).path("userId").asText());
        assertTrue(hash.startsWith("$argon2id$"));assertFalse(hash.contains(password));
    }
    @Test void duplicatesInvalidCredentialsAndRoleInjectionNeverCreatePrivilege() throws Exception {
        var browser=csrf(null);String email=UUID.randomUUID()+"@example.com";
        postJson("/auth/register",browser,Map.of("email",email,"password","Example123"),201);
        var duplicate=postJson("/auth/register",browser,Map.of("email",email.toUpperCase(java.util.Locale.ROOT),"password","Example123"),409);
        assertEquals("EMAIL_ALREADY_REGISTERED",json.readTree(duplicate.getContentAsString()).path("code").asText());
        var wrong=postJson("/auth/login",browser,Map.of("email",email,"password","Incorrect123"),401);
        assertEquals("INVALID_CREDENTIALS",json.readTree(wrong.getContentAsString()).path("code").asText());
        postJson("/auth/register",browser,Map.of("email",UUID.randomUUID()+"@example.com","password","Example123","role","Admin"),400);
        mvc.perform(get("/api/v1/admin/dishes").cookie(browser.cookie())).andExpect(status().isUnauthorized());
    }
    @Test void inputBoundariesAndPasswordsAreNotTruncatedOrTrimmed() throws Exception {
        var browser=csrf(null);
        for(String password:new String[]{"A123456","abcdefgh","12345678","A1"+"🍜".repeat(127)})
            postJson("/auth/register",browser,Map.of("email",UUID.randomUUID()+"@example.com","password",password),400);
        for(String email:new String[]{"invalid"," ","a".repeat(190)+"@example.com"})
            postJson("/auth/register",browser,Map.of("email",email,"password","Example123"),400);
        String email=UUID.randomUUID()+"@example.com",password="A1"+"🍜".repeat(126);
        postJson("/auth/register",browser,Map.of("email",email,"password",password),201);
        postJson("/auth/login",browser,Map.of("email",email,"password",password.substring(0,128)),401);
        postJson("/auth/login",browser,Map.of("email",email,"password",password),200);
    }
    @Test void controlledAdminInitializationDoesNotResetOrElevateAccounts() throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        var admin=admins.initialize(email,"AdminExample123");
        assertEquals(com.webox.shared.DomainTypes.Role.Admin,admin.role());
        String before=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,admin.userId());
        assertEquals(admin,admins.initialize(email,null));
        assertEquals(before,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,admin.userId()));
        var browser=csrf(null);var login=postJson("/auth/login",browser,Map.of("email",email,"password","AdminExample123"),200);
        mvc.perform(get("/api/v1/admin/not-a-route").cookie(login.getCookie("WEBOXSESSION"))).andExpect(status().isNotFound());
        String employeeEmail=UUID.randomUUID()+"@example.com";
        postJson("/auth/register",csrf(null),Map.of("email",employeeEmail,"password","Employee123"),201);
        assertThrows(IllegalStateException.class,()->admins.initialize(employeeEmail,"AdminExample123"));
        assertEquals("Employee",jdbc.queryForObject("SELECT role FROM users WHERE normalized_email=?",String.class,employeeEmail));
    }

    @Test void eightHourIdleSessionCannotBeReused() throws Exception {
        var anonymous=csrf(null);String email=UUID.randomUUID()+"@example.com";
        postJson("/auth/register",anonymous,Map.of("email",email,"password","Example123"),201);
        Cookie cookie=postJson("/auth/login",anonymous,Map.of("email",email,"password","Example123"),200).getCookie("WEBOXSESSION");
        assertEquals(28800,jdbc.queryForObject("SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID=?",Integer.class,cookie.getValue()));
        long expired=System.currentTimeMillis()-28800_000L-1000;
        jdbc.update("UPDATE SPRING_SESSION SET LAST_ACCESS_TIME=?,EXPIRY_TIME=? WHERE SESSION_ID=?",expired,expired+28800_000L,cookie.getValue());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test void internalErrorDispatchCanFinishAnExpiredStreamWithoutOpeningTheErrorRoute() throws Exception {
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        var response=mvc.perform(get("/error").with(request->{request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);return request;})
            .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE,500)
            .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_MESSAGE,"Internal detail must stay private"))
            .andExpect(status().isInternalServerError()).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("Internal detail"));assertFalse(response.getContentAsString().contains("trace"));
        // Tomcat includes /error after an already committed SSE response; requestURI still names the original stream.
        mvc.perform(get("/aborted-stream").with(request->{request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);return request;})
            .requestAttr(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI,"/error"))
            .andExpect(status().isInternalServerError());
        mvc.perform(get("/aborted-stream").with(request->{request.setDispatcherType(jakarta.servlet.DispatcherType.INCLUDE);return request;})
            .requestAttr(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI,"/error")
            .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION,new java.io.IOException("Closed client")))
            .andExpect(status().isInternalServerError());
        mvc.perform(get("/api/v1/admin/dashboard").with(request->{request.setDispatcherType(jakarta.servlet.DispatcherType.INCLUDE);return request;})
            .requestAttr(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI,"/error"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").with(request->{request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);return request;})
            .requestAttr(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI,"/api/v1/admin/dashboard"))
            .andExpect(status().isUnauthorized());
    }

}
