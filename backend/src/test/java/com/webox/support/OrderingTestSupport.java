package com.webox.support;
import java.util.*;
import com.webox.auth.AuthService;
import com.webox.shared.DomainTypes.Role;
import com.webox.media.MediaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
public abstract class OrderingTestSupport extends MySqlTestSupport {
    @Autowired protected MockMvc mvc;@Autowired protected JdbcTemplate jdbc;@Autowired protected JsonMapper json;
    @Autowired protected JdbcIndexedSessionRepository sessions;@Autowired protected AuthService auth;@Autowired protected MediaRepository media;@Autowired protected PlatformTransactionManager manager;
    public record Browser(FixtureFactory.IdentitySession identity,String csrf) {}
    protected Browser browser() throws Exception {
        var identity=new FixtureFactory(sessions,auth).identity(Role.Employee);
        var response=mvc.perform(get("/api/v1/auth/csrf").cookie(identity.cookie())).andReturn().getResponse();assertEquals(200,response.getStatus());
        return new Browser(identity,json.readTree(response.getContentAsString()).get("token").asString());
    }
    protected CatalogFixtures.Menu menu() { return new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,manager).create(); }
    protected MockHttpServletResponse postJson(String path,Browser user,Object body,String key) throws Exception {
        var request=post("/api/v1"+path).cookie(user.identity().cookie()).header("X-XSRF-TOKEN",user.csrf());
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));if(key!=null) request.header("Idempotency-Key",key);
        return mvc.perform(request).andReturn().getResponse();
    }
    protected MockHttpServletResponse read(String path,Browser user) throws Exception { return mvc.perform(get("/api/v1"+path).cookie(user.identity().cookie())).andReturn().getResponse(); }
    protected Map<String,Object> line(String dish,long version,int quantity,String... options) { return Map.of("dishId",dish,"dishVersion",version,"quantity",quantity,"optionIds",List.of(options)); }
    protected Map<String,Object> request(CatalogFixtures.Menu menu,int quantity) { return Map.of("deliveryDate",menu.date().toString(),"meal","Lunch","items",List.of(line(menu.pasta(),1,quantity,menu.options().get("Spaghetti"),menu.options().get("Cheese")))); }
    protected String quote(Browser user,CatalogFixtures.Menu menu,int quantity) throws Exception {
        var response=postJson("/checkout/quotes",user,request(menu,quantity),null);assertEquals(201,response.getStatus(),"Quote must be available");ContractAssertions.matches("Quote",response.getContentAsString());return field(response,"quoteId");
    }
    protected String responseBody(MockHttpServletResponse response) { return new String(response.getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8); }
    protected String field(MockHttpServletResponse response,String name) { return json.readTree(responseBody(response)).path(name).asString(); }
    protected void error(MockHttpServletResponse response,int status,String code) { assertEquals(status,response.getStatus());ContractAssertions.matches("Error",responseBody(response));assertEquals(code,field(response,"code")); }
}
