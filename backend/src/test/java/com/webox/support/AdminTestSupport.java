package com.webox.support;
import java.util.*;
import com.webox.shared.DomainTypes.Role;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
public abstract class AdminTestSupport extends OrderingTestSupport {
    protected Browser admin() throws Exception {
        var identity=new FixtureFactory(sessions,auth).identity(Role.Admin);var response=mvc.perform(get("/api/v1/auth/csrf").cookie(identity.cookie())).andReturn().getResponse();assertEquals(200,response.getStatus());return new Browser(identity,json.readTree(responseBody(response)).path("token").asString());
    }
    protected MockHttpServletResponse write(String method,String path,Browser user,Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method),"/api/v1"+path).cookie(user.identity().cookie()).header("X-XSRF-TOKEN",user.csrf()).contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse();
    }
    protected Map<String,Object> newDish(String media) {
        var body=new LinkedHashMap<String,Object>();body.put("name","Test Burger");body.put("description","Grilled beef with fresh lettuce.");body.put("basePriceCents",3800);body.put("category","Western");body.put("protein","Beef");body.put("allergens",List.of("Gluten"));body.put("spiceLevel","None");body.put("mediaId",media);body.put("optionGroups",List.of(Map.of("name","Bun","required",true,"selectionMode","Single","options",List.of(Map.of("name","Plain","extraCents",0,"allergens",List.of("Gluten")),Map.of("name","Cheese bun","extraCents",300,"allergens",List.of("Gluten","Dairy"))))));return body;
    }
    @SuppressWarnings("unchecked") protected Map<String,Object> editable(MockHttpServletResponse response) {
        var body=new LinkedHashMap<>((Map<String,Object>)json.readValue(responseBody(response),Map.class));body.put("expectedVersion",body.remove("dishVersion"));for(String field:List.of("dishId","imageUrl","published"))body.remove(field);return body;
    }
    protected void dishResponse(MockHttpServletResponse response,int status) { assertEquals(status,response.getStatus(),responseBody(response));ContractAssertions.matches("AdminDish",responseBody(response)); }
}
