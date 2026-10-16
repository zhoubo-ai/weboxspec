package com.webox.auth;
import com.webox.support.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class PreferencesContractIT extends OrderingTestSupport {
    private Map<String,Object> prefs(long version) {
        var body=new LinkedHashMap<String,Object>();body.put("allergens",List.of());body.put("cuisines",List.of());body.put("spiceLevel",null);body.put("flavor",null);body.put("budgetMinCents",null);body.put("budgetMaxCents",null);body.put("expectedVersion",version);return body;
    }
    private MockHttpServletResponse save(Browser user,Map<String,Object> body) throws Exception {
        return mvc.perform(put("/api/v1/me/preferences").cookie(user.identity().cookie()).header("X-XSRF-TOKEN",user.csrf()).contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse();
    }
    private void success(MockHttpServletResponse response,long version) {assertEquals(200,response.getStatus());ContractAssertions.matches("Preferences",responseBody(response));assertEquals(version,json.readTree(responseBody(response)).path("version").asLong());}
    @Test void replaceClearAndReloadAreOwnedAndVersioned() throws Exception {
        var a=browser();var b=browser();success(read("/me/preferences",a),0);
        var body=prefs(0);body.put("allergens",List.of("Egg","Dairy"));body.put("cuisines",List.of("Japanese","Light Meal"));body.put("spiceLevel","None");body.put("flavor","Light");body.put("budgetMinCents",1500);body.put("budgetMaxCents",3000);
        var saved=save(a,body);success(saved,1);assertEquals(json.readTree(responseBody(saved)),json.readTree(responseBody(read("/me/preferences",a))));success(read("/me/preferences",b),0);
        body.put("userId",b.identity().user().userId());error(save(a,body),400,"VALIDATION_ERROR");body.remove("userId");error(save(a,body),409,"VERSION_CONFLICT");
        success(save(a,prefs(1)),2);var cleared=json.readTree(responseBody(read("/me/preferences",a)));assertEquals(0,cleared.path("allergens").size());assertTrue(cleared.path("budgetMinCents").isNull());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dietary_preferences WHERE user_id=?",Integer.class,a.identity().user().userId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM preference_allergens WHERE user_id=?",Integer.class,a.identity().user().userId()));
        assertEquals(403,mvc.perform(put("/api/v1/me/preferences").cookie(a.identity().cookie()).contentType("application/json").content(json.writeValueAsString(prefs(2)))).andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(get("/api/v1/me/preferences")).andReturn().getResponse().getStatus());
    }
    @Test void invalidBudgetsEnumsDuplicatesAndMissingFieldsDoNotWrite() throws Exception {
        var user=browser();success(read("/me/preferences",user),0);
        for(var pair:List.of(List.of(-1,3000),List.of(3001,3000),List.of(0,1.5),List.of(0,9007199254740992L))) {var body=prefs(0);body.put("budgetMinCents",pair.get(0));body.put("budgetMaxCents",pair.get(1));error(save(user,body),400,"VALIDATION_ERROR");}
        for(String bound:List.of("budgetMinCents","budgetMaxCents")) {var body=prefs(0);body.put(bound,3000);error(save(user,body),400,"VALIDATION_ERROR");}
        for(var invalid:List.of(Map.of("allergens",List.of("Egg","Egg")),Map.of("allergens",List.of("Unknown")),Map.of("cuisines",List.of("Japanese","Japanese")),Map.of("spiceLevel",2),Map.of("flavor","Unknown"),Map.of("expectedVersion",-1))) {var body=prefs(0);body.putAll(invalid);error(save(user,body),400,"VALIDATION_ERROR");}
        for(String missing:prefs(0).keySet()) {var body=prefs(0);body.remove(missing);error(save(user,body),400,"VALIDATION_ERROR");}
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dietary_preferences WHERE user_id=?",Integer.class,user.identity().user().userId()));
    }
    @Test void simultaneousReplacementsHaveOneWinnerAndNoMixedCollections() throws Exception {
        var user=browser();success(save(user,prefs(0)),1);var executor=Executors.newFixedThreadPool(8);var gate=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<MockHttpServletResponse>>();for(int i=0;i<12;i++) {final boolean first=i%2==0;futures.add(executor.submit(()->{gate.await();var body=prefs(1);body.put("allergens",List.of(first?"Egg":"Dairy"));body.put("cuisines",List.of(first?"Japanese":"Western"));return save(user,body);}));}gate.countDown();int winners=0;
            for(var future:futures) {var result=future.get(15,TimeUnit.SECONDS);if(result.getStatus()==200){winners++;success(result,2);}else error(result,409,"VERSION_CONFLICT");}assertEquals(1,winners);
            var result=json.readTree(responseBody(read("/me/preferences",user)));assertEquals(result.path("allergens").get(0).asString().equals("Egg")?"Japanese":"Western",result.path("cuisines").get(0).asString());
        } finally {executor.shutdownNow();}
    }
    @Test void recommendedOrderingKeepsFiltersAndPersonalMatchesOutOfThePublicCache() throws Exception {
        var user=browser();var other=browser();var menu=menu();var body=prefs(0);body.put("cuisines",List.of("Japanese"));body.put("spiceLevel","None");success(save(user,body),1);
        jdbc.update("UPDATE dishes SET spice='Hot' WHERE id=?",menu.japanese());jdbc.update("UPDATE dishes SET category='Japanese',name='Z Japanese Salad' WHERE id=?",menu.unscheduled());jdbc.update("UPDATE daily_supply SET scheduled=TRUE WHERE delivery_date=? AND dish_id=?",menu.date(),menu.unscheduled());
        String url="/menus/"+menu.date();var defaultResponse=read(url,user);assertEquals(200,defaultResponse.getStatus());var defaultItems=json.readTree(responseBody(defaultResponse)).path("items");
        var response=read(url+"?recommended=true",user);assertEquals(200,response.getStatus());ContractAssertions.matches("MenuPage",responseBody(response));var items=json.readTree(responseBody(response)).path("items");assertEquals(menu.unscheduled(),items.get(0).path("dishId").asString());assertEquals(menu.japanese(),items.get(1).path("dishId").asString());assertTrue(items.get(0).path("preferenceMatch").path("spice").asBoolean());assertFalse(items.get(1).path("preferenceMatch").path("spice").asBoolean());
        var filtered=json.readTree(responseBody(read(url+"?recommended=true&categories=Japanese&categories=Chinese&search=Salad",user)));assertEquals(3,filtered.path("totalElements").asInt());
        var otherItems=json.readTree(responseBody(read(url+"?recommended=true",other))).path("items");for(int i=0;i<defaultItems.size();i++){assertEquals(defaultItems.get(i).path("dishId"),otherItems.get(i).path("dishId"));assertFalse(otherItems.get(i).path("preferenceMatch").path("cuisine").asBoolean());assertFalse(otherItems.get(i).path("preferenceMatch").path("spice").asBoolean());}
    }
    @Test void warningsUseSelectedAllergensAndFinalMergedIndexesWithStrictBudgetUpperBound() throws Exception {
        var user=browser();var menu=menu();var body=prefs(0);body.put("allergens",List.of("Egg","Dairy"));body.put("budgetMinCents",2900);body.put("budgetMaxCents",3000);success(save(user,body),1);
        var burger=line(menu.burger(),1,1,menu.options().get("Plain bun"),menu.options().get("Mayonnaise"));var request=new HashMap<String,Object>(request(menu,1));request.put("items",List.of(burger,line(menu.pasta(),1,1,menu.options().get("Spaghetti")),line(menu.pasta(),1,1,menu.options().get("Spaghetti"),menu.options().get("Cheese")),burger));
        var response=postJson("/checkout/quotes",user,request,null);assertEquals(201,response.getStatus());ContractAssertions.matches("Quote",responseBody(response));var quote=json.readTree(responseBody(response));assertEquals(3,quote.path("items").size());int flags=0;
        for(var warning:quote.path("warnings")) if(warning.path("code").asString().equals("FLAGGED_ALLERGEN")) {flags++;var item=quote.path("items").get(warning.path("itemIndex").asInt());assertEquals(item.path("dishId"),warning.path("dishId"));assertEquals(1,warning.path("allergens").size());assertTrue(List.of("Egg","Dairy").contains(warning.path("allergens").get(0).asString()));}
        assertEquals(2,flags);
        jdbc.update("INSERT INTO dish_allergens VALUES (?,?)",menu.chinese(),"Egg");
        var baseOnly=new HashMap<String,Object>(request(menu,1));baseOnly.put("items",List.of(line(menu.chinese(),1,1)));var baseResult=postJson("/checkout/quotes",user,baseOnly,null);assertEquals(201,baseResult.getStatus());var baseWarnings=json.readTree(responseBody(baseResult)).path("warnings");assertEquals(1,baseWarnings.size());assertEquals("Egg",baseWarnings.get(0).path("allergens").get(0).asString());jdbc.update("DELETE FROM dish_allergens WHERE dish_id=?",menu.chinese());
        for(int price:List.of(2650,3000,3001)) {
            jdbc.update("UPDATE dishes SET base_price_cents=?,version=version+1 WHERE id=?",price,menu.chinese());long version=jdbc.queryForObject("SELECT version FROM dishes WHERE id=?",Long.class,menu.chinese());var simple=new HashMap<String,Object>(request(menu,1));simple.put("items",List.of(line(menu.chinese(),version,1)));var result=postJson("/checkout/quotes",user,simple,null);assertEquals(201,result.getStatus());ContractAssertions.matches("Quote",responseBody(result));var tree=json.readTree(responseBody(result));assertEquals(price>3000?1:0,tree.path("warnings").size());
            if(price>3000) {assertEquals("BUDGET_EXCEEDED",tree.path("warnings").get(0).path("code").asString());assertEquals(201,postJson("/orders",user,Map.of("quoteId",field(result,"quoteId"),"addressText","Office"),UUID.randomUUID().toString()).getStatus());}
        }
    }
}
