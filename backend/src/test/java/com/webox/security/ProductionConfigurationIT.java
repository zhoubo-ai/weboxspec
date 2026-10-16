package com.webox.security;
import com.webox.support.AdminTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
@SpringBootTest
@ActiveProfiles({"test","prod"})
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class ProductionConfigurationIT extends AdminTestSupport {
    @Test void productionCookiesRequireHttpsAndMetricsRequireAnAdministrator()throws Exception {
        var response=mvc.perform(get("/api/v1/auth/csrf").secure(true)).andReturn().getResponse();assertEquals(200,response.getStatus());var cookie=response.getCookie("WEBOXSESSION");assertNotNull(cookie);assertTrue(cookie.getSecure());assertTrue(cookie.isHttpOnly());assertEquals("Lax",cookie.getAttribute("SameSite"));
        assertEquals(401,mvc.perform(get("/actuator/metrics")).andReturn().getResponse().getStatus());var employee=browser();assertEquals(403,mvc.perform(get("/actuator/metrics").cookie(employee.identity().cookie())).andReturn().getResponse().getStatus());var actor=admin();assertEquals(200,mvc.perform(get("/actuator/metrics").cookie(actor.identity().cookie())).andReturn().getResponse().getStatus());assertEquals(403,mvc.perform(get("/actuator/env").cookie(actor.identity().cookie())).andReturn().getResponse().getStatus());
    }
}
