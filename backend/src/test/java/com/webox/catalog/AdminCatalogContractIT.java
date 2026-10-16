package com.webox.catalog;
import com.webox.support.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AdminCatalogContractIT extends AdminTestSupport {
    @Test void optionalIdentifiersCanBeOmittedButCannotBeExplicitNull() throws Exception {
        var admin=admin();var fixture=menu();var current=read("/admin/dishes/"+fixture.pasta(),admin);var body=editable(current);
        var option=new LinkedHashMap<String,Object>();option.put("name","New option");option.put("extraCents",0);option.put("allergens",List.of());option.put("optionId",null);
        var group=new LinkedHashMap<String,Object>();group.put("name","Extras");group.put("required",false);group.put("selectionMode","Multiple");group.put("options",List.of(option));body.put("optionGroups",List.of(group));
        error(write("PUT","/admin/dishes/"+fixture.pasta(),admin,body),400,"VALIDATION_ERROR");option.remove("optionId");group.put("groupId",null);error(write("PUT","/admin/dishes/"+fixture.pasta(),admin,body),400,"VALIDATION_ERROR");
        group.remove("groupId");dishResponse(write("PUT","/admin/dishes/"+fixture.pasta(),admin,body),200);
    }
    @Test void realAdministrativeChangesLeaveHistoricalOrderAndMediaSnapshotsIntact() throws Exception {
        var admin=admin();var user=browser();var fixture=menu();String quote=quote(user,fixture,1);var placed=postJson("/orders",user,Map.of("quoteId",quote,"addressText","Historical office"),UUID.randomUUID().toString());assertEquals(201,placed.getStatus());
        var before=json.readTree(responseBody(placed));String oldImage=field(read("/admin/dishes/"+fixture.pasta(),admin),"imageUrl");
        var current=read("/admin/dishes/"+fixture.pasta(),admin);var body=editable(current);body.put("name","Updated Pasta");body.put("basePriceCents",3100);body.put("allergens",List.of("Egg"));body.put("mediaId",fixture.mediaId());body.put("optionGroups",List.of());dishResponse(write("PUT","/admin/dishes/"+fixture.pasta(),admin,body),200);
        dishResponse(write("PATCH","/admin/dishes/"+fixture.pasta()+"/publication",admin,Map.of("published",false,"expectedVersion",2)),200);
        var after=json.readTree(responseBody(read("/orders/"+field(placed,"orderId"),user)));assertEquals(before,after);assertEquals(200,mvc.perform(get(oldImage)).andReturn().getResponse().getStatus());error(read("/menus/"+fixture.date()+"/dishes/"+fixture.pasta(),user),404,"NOT_FOUND");
    }
    @Test void fullDishLifecyclePreservesIdsAndInvalidatesVersionsAndPublishedMenu() throws Exception {
        var admin=admin();var employee=browser();var fixture=menu();var body=newDish(fixture.mediaId());var created=write("POST","/admin/dishes",admin,body);dishResponse(created,201);String id=field(created,"dishId");var tree=json.readTree(responseBody(created));assertFalse(tree.path("published").asBoolean());assertEquals(1,tree.path("dishVersion").asLong());
        var fetched=read("/admin/dishes/"+id,admin);dishResponse(fetched,200);assertEquals(tree,json.readTree(responseBody(fetched)));
        var listed=read("/admin/dishes?search=Test&categories=Western&published=false&size=1",admin);assertEquals(200,listed.getStatus());ContractAssertions.matches("AdminDishPage",responseBody(listed));assertEquals(id,json.readTree(responseBody(listed)).path("items").get(0).path("dishId").asString());
        var update=editable(created);update.put("basePriceCents",3901);var changed=write("PUT","/admin/dishes/"+id,admin,update);dishResponse(changed,200);assertEquals(2,json.readTree(responseBody(changed)).path("dishVersion").asLong());assertEquals(tree.path("optionGroups"),json.readTree(responseBody(changed)).path("optionGroups"));error(write("PUT","/admin/dishes/"+id,admin,update),409,"VERSION_CONFLICT");
        var publication=write("PATCH","/admin/dishes/"+id+"/publication",admin,Map.of("published",true,"expectedVersion",2));dishResponse(publication,200);
        var scheduled=write("PUT","/admin/menus/"+fixture.date(),admin,Map.of("expectedRevision",1,"items",List.of(Map.of("dishId",id,"totalQuantity",8))));assertEquals(200,scheduled.getStatus());ContractAssertions.matches("DailyMenu",responseBody(scheduled));assertEquals(200,read("/menus/"+fixture.date()+"/dishes/"+id,employee).getStatus());
        var unpublished=write("PATCH","/admin/dishes/"+id+"/publication",admin,Map.of("published",false,"expectedVersion",3));dishResponse(unpublished,200);error(read("/menus/"+fixture.date()+"/dishes/"+id,employee),404,"NOT_FOUND");assertEquals(3,jdbc.queryForObject("SELECT revision FROM daily_menus WHERE delivery_date=?",Integer.class,fixture.date()));assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_key=? AND event_type='menu.changed'",Integer.class,fixture.date().toString())>=2);
    }
    @Test void validationRejectsInvalidFieldsForeignOptionsAndUnverifiedMediaWithoutChangingTheDish() throws Exception {
        var admin=admin();var fixture=menu();var created=write("POST","/admin/dishes",admin,newDish(fixture.mediaId()));dishResponse(created,201);String id=field(created,"dishId");
        for(var patch:List.of(Map.of("basePriceCents",-1),Map.of("category","Unknown"),Map.of("name","中文名称"),Map.of("name"," "),Map.of("mediaId",UUID.randomUUID().toString()),Map.of("allergens",List.of("Egg","Egg")),Map.of("optionGroups",List.of(Map.of("name","Empty","required",false,"selectionMode","Multiple","options",List.of()))))) {var invalid=newDish(fixture.mediaId());invalid.putAll(patch);error(write("POST","/admin/dishes",admin,invalid),400,"VALIDATION_ERROR");}
        for(String missing:newDish(fixture.mediaId()).keySet()) {var invalid=newDish(fixture.mediaId());invalid.remove(missing);error(write("POST","/admin/dishes",admin,invalid),400,"VALIDATION_ERROR");}
        var invalid=editable(created);invalid.put("optionGroups",List.of(Map.of("name","Foreign","required",false,"selectionMode","Multiple","options",List.of(Map.of("optionId",fixture.options().get("Spaghetti"),"name","Foreign","extraCents",0,"allergens",List.of())))));error(write("PUT","/admin/dishes/"+id,admin,invalid),400,"VALIDATION_ERROR");assertEquals(1,jdbc.queryForObject("SELECT version FROM dishes WHERE id=?",Integer.class,id));
        error(write("PATCH","/admin/dishes/"+id+"/publication",admin,Map.of("published",true,"expectedVersion",0)),409,"VERSION_CONFLICT");
    }
    @Test void supplyReplacementRetainsAllocationsAndOmittedRowsAndIsAtomic() throws Exception {
        var admin=admin();var user=browser();var fixture=menu();String quote=quote(user,fixture,1);var placed=postJson("/orders",user,Map.of("quoteId",quote,"addressText","Office"),UUID.randomUUID().toString());assertEquals(201,placed.getStatus());
        var current=read("/admin/menus/"+fixture.date(),admin);assertEquals(200,current.getStatus());ContractAssertions.matches("DailyMenu",responseBody(current));
        error(write("PUT","/admin/menus/"+fixture.date(),admin,Map.of("expectedRevision",1,"items",List.of(Map.of("dishId",fixture.pasta(),"totalQuantity",0)))),409,"SUPPLY_BELOW_ALLOCATED");assertEquals(1,jdbc.queryForObject("SELECT revision FROM daily_menus WHERE delivery_date=?",Integer.class,fixture.date()));
        var duplicate=Map.of("dishId",fixture.pasta(),"totalQuantity",4);error(write("PUT","/admin/menus/"+fixture.date(),admin,Map.of("expectedRevision",1,"items",List.of(duplicate,duplicate))),400,"VALIDATION_ERROR");
        var changed=write("PUT","/admin/menus/"+fixture.date(),admin,Map.of("expectedRevision",1,"items",List.of(duplicate)));assertEquals(200,changed.getStatus());assertEquals(4,jdbc.queryForObject("SELECT total_quantity FROM daily_supply WHERE delivery_date=? AND dish_id=?",Integer.class,fixture.date(),fixture.pasta()));
        var removed=write("PUT","/admin/menus/"+fixture.date(),admin,Map.of("expectedRevision",2,"items",List.of()));assertEquals(200,removed.getStatus());var row=jdbc.queryForMap("SELECT total_quantity,allocated_quantity,scheduled FROM daily_supply WHERE delivery_date=? AND dish_id=?",fixture.date(),fixture.pasta());assertEquals(4,((Number)row.get("total_quantity")).intValue());assertEquals(1,((Number)row.get("allocated_quantity")).intValue());assertEquals(false,row.get("scheduled"));assertEquals("Pending",field(read("/orders/"+field(placed,"orderId"),user),"status"));
        var missingDate=fixture.date().plusYears(3);var empty=read("/admin/menus/"+missingDate,admin);assertEquals(200,empty.getStatus());assertEquals(0,json.readTree(responseBody(empty)).path("revision").asLong());
        assertEquals(200,write("PUT","/admin/menus/"+missingDate,admin,Map.of("expectedRevision",0,"items",List.of(Map.of("dishId",fixture.pasta(),"totalQuantity",20)))).getStatus());
    }
    @Test void twoEditorsWithTheSameMenuRevisionCannotOverwriteEachOther() throws Exception {
        var admin=admin();var fixture=menu();assertEquals(200,read("/admin/menus/"+fixture.date(),admin).getStatus());var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {var jobs=new ArrayList<Future<Integer>>();for(int total:List.of(30,40))jobs.add(executor.submit(()->{start.await();return write("PUT","/admin/menus/"+fixture.date(),admin,Map.of("expectedRevision",1,"items",List.of(Map.of("dishId",fixture.pasta(),"totalQuantity",total)))).getStatus();}));start.countDown();var statuses=new ArrayList<Integer>();for(var job:jobs)statuses.add(job.get(10,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}finally{executor.shutdownNow();}
    }
    @Test void administratorRoutesEnforceRoleAndCsrfBeforeMutations() throws Exception {
        var admin=admin();var employee=browser();var fixture=menu();assertEquals(200,read("/admin/dishes",admin).getStatus());
        for(String path:List.of("/admin/dishes","/admin/dishes/"+fixture.pasta(),"/admin/menus/"+fixture.date()))error(read(path,employee),403,"FORBIDDEN");
        error(write("POST","/admin/dishes",employee,newDish(fixture.mediaId())),403,"FORBIDDEN");
        assertEquals(403,mvc.perform(post("/api/v1/admin/dishes").cookie(admin.identity().cookie()).contentType("application/json").content(json.writeValueAsString(newDish(fixture.mediaId())))).andReturn().getResponse().getStatus());
        assertEquals(401,mvc.perform(get("/api/v1/admin/dishes")).andReturn().getResponse().getStatus());
    }
}
