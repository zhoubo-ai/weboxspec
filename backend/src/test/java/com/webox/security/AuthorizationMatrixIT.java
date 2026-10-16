package com.webox.security;
import com.webox.support.*;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.yaml.snakeyaml.Yaml;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AuthorizationMatrixIT extends AdminTestSupport {
    record Client(Cookie cookie,String token){}
    Client client(Browser browser){return new Client(browser.identity().cookie(),browser.csrf());}
    Client anonymous()throws Exception {var response=mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();return new Client(response.getCookie("WEBOXSESSION"),field(response,"token"));}
    MockHttpServletResponse call(String method,String path,Client actor,boolean csrf)throws Exception {
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(method),path).cookie(actor.cookie());if(!method.equals("GET")){request.contentType("application/json").content("{}").header("Idempotency-Key",UUID.randomUUID().toString());if(csrf)request.header("X-XSRF-TOKEN",actor.token());}return mvc.perform(request).andReturn().getResponse();
    }
    @SuppressWarnings("unchecked") @Test void everyContractOperationEnforcesRoleAndWriteCsrfBeforeBusinessValidation()throws Exception {
        Map<String,Object> contract;try(var input=getClass().getResourceAsStream("/contracts/openapi.yaml")){contract=new Yaml().load(input);}
        var paths=(Map<String,Map<String,Object>>)contract.get("paths");var roles=new LinkedHashMap<String,Client>();roles.put("Anonymous",anonymous());roles.put("Employee",client(browser()));roles.put("Admin",client(admin()));var results=new ArrayList<Map<String,Object>>();int operations=0;
        for(var entry:paths.entrySet())for(var action:entry.getValue().entrySet()) {
            String method=action.getKey().toUpperCase(Locale.ROOT);if(!Set.of("GET","POST","PUT","PATCH","DELETE").contains(method))continue;operations++;String route=entry.getKey();String path=(route.startsWith("/media/")?"":"/api/v1")+route.replace("{date}","invalid-date").replaceAll("\\{[^}]+}","00000000-0000-4000-8000-000000000001");boolean publicAccess=Set.of("/health","/auth/csrf","/auth/register","/auth/login").contains(route)||route.startsWith("/media/");
            for(var role:roles.entrySet()) {
                Client actor=route.equals("/auth/logout")?switch(role.getKey()){case "Admin"->client(admin());case "Employee"->client(browser());default->anonymous();}:role.getValue();
                // A missing token must reject every write, including login/register and multipart upload.
                if(!method.equals("GET"))error(call(method,path,actor,false),403,"CSRF_INVALID");
                var response=call(method,path,actor,true);int status=response.getStatus();String label=method+" "+route+" as "+role.getKey();
                if(role.getKey().equals("Anonymous")&&!publicAccess)assertEquals(401,status,label);
                else if(role.getKey().equals("Employee")&&route.startsWith("/admin/"))assertEquals(403,status,label);
                else assertTrue(Set.of(200,204,400,404,415).contains(status),label+" must reach the handler, got "+status);
                results.add(Map.of("operationId",((Map<String,Object>)action.getValue()).get("operationId"),"method",method,"path",route,"role",role.getKey(),"status",status,"missingCsrfRejected",!method.equals("GET")));
            }
        }
        assertEquals(32,operations);assertEquals(96,results.size());Path report=Path.of("target/security-authorization-matrix.json");Files.createDirectories(report.getParent());Files.writeString(report,json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }
    @Test void hostileSearchIsLiteralAndUserQueryParametersDoNotChangeOwnership()throws Exception {
        var menu=menu();var owner=browser();var other=browser();String quote=quote(owner,menu,1);var order=postJson("/orders",owner,Map.of("quoteId",quote,"addressText","Private office"),UUID.randomUUID().toString());assertEquals(201,order.getStatus());String ownerId=owner.identity().user().userId();
        assertEquals(0,json.readTree(responseBody(read("/orders?userId="+ownerId,other))).path("totalElements").asInt());assertEquals(0,json.readTree(responseBody(read("/me/addresses?userId="+ownerId,other))).size());
        var preferences=new LinkedHashMap<String,Object>();preferences.put("allergens",List.of("Egg"));preferences.put("cuisines",List.of());preferences.put("spiceLevel",null);preferences.put("flavor",null);preferences.put("budgetMinCents",null);preferences.put("budgetMaxCents",null);preferences.put("expectedVersion",0);assertEquals(200,write("PUT","/me/preferences",owner,preferences).getStatus());assertEquals(0,json.readTree(responseBody(read("/me/preferences?userId="+ownerId,other))).path("allergens").size());preferences.put("userId",ownerId);error(write("PUT","/me/preferences",other,preferences),400,"VALIDATION_ERROR");
        var response=mvc.perform(get("/api/v1/menus/"+menu.date()).param("search","%' OR 1=1 --").cookie(other.identity().cookie())).andReturn().getResponse();assertEquals(200,response.getStatus());assertEquals(0,json.readTree(responseBody(response)).path("totalElements").asInt());
        for(int count:List.of(50,51)){response=mvc.perform(get("/api/v1/menus/"+menu.date()).param("search","🥗".repeat(count)).cookie(other.identity().cookie())).andReturn().getResponse();assertEquals(count==50?200:400,response.getStatus());}
        error(read("/orders/"+field(order,"orderId"),other),404,"NOT_FOUND");assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id=?",Integer.class,ownerId));
    }
}
