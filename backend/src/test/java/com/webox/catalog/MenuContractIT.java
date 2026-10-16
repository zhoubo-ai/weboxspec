package com.webox.catalog;
import com.webox.support.*;
import com.webox.auth.AuthService;
import com.webox.shared.DomainTypes.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.webox.media.MediaRepository;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class MenuContractIT extends MySqlTestSupport {
    @Autowired MockMvc mvc;@Autowired JdbcIndexedSessionRepository sessions;@Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;@Autowired MediaRepository media;@Autowired PlatformTransactionManager transactions;@Autowired JsonMapper json;
    CatalogFixtures fixture() { return new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,transactions); }
    @Test void emptyDateReturnsACompleteContractWithoutCreatingAMenu() throws Exception {
        var user=new FixtureFactory(sessions,auth).identity(Role.Employee);
        var result=mvc.perform(get("/api/v1/menus/2030-01-01").cookie(user.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.menuRevision").value(0)).andReturn().getResponse();
        ContractAssertions.matches("MenuPage",result.getContentAsString());
    }
    @Test void onlyListedAndScheduledDishesAppearIncludingSoldOutAndCompleteOptions() throws Exception {
        var catalog=fixture().create();var user=new FixtureFactory(sessions,auth).identity(Role.Employee);
        String body=mvc.perform(get("/api/v1/menus/"+catalog.date()).cookie(user.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5)).andExpect(jsonPath("$.size").value(24)).andReturn().getResponse().getContentAsString();
        ContractAssertions.matches("MenuPage",body);assertFalse(body.contains(catalog.hidden()));assertFalse(body.contains(catalog.unscheduled()));
        String pasta=mvc.perform(get("/api/v1/menus/"+catalog.date()+"/dishes/"+catalog.pasta()).cookie(user.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.basePriceCents").value(2650)).andExpect(jsonPath("$.optionGroups[1].options[0].extraCents").value(300)).andExpect(jsonPath("$.optionGroups[1].options[0].allergens[0]").value("Dairy")).andReturn().getResponse().getContentAsString();
        ContractAssertions.matches("MenuDish",pasta);
        mvc.perform(get("/api/v1/menus/"+catalog.date()+"/dishes/"+catalog.salad()).cookie(user.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.remaining").value(0));
        for(String id:List.of(catalog.hidden(),catalog.unscheduled(),UUID.randomUUID().toString())) mvc.perform(get("/api/v1/menus/"+catalog.date()+"/dishes/"+id).cookie(user.cookie())).andExpect(status().isNotFound());
    }
    @Test void categoryUnionCombinesWithLiteralCaseInsensitiveSearchAndUnicodeLimits() throws Exception {
        var catalog=fixture().create();var user=new FixtureFactory(sessions,auth).identity(Role.Employee);String url="/api/v1/menus/"+catalog.date();
        mvc.perform(get(url).cookie(user.cookie()).param("categories","Chinese","Japanese").param("search","gReEnS")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        for(String term:List.of("%","_","!","100%","dressing_!")) mvc.perform(get(url).cookie(user.cookie()).param("search",term)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].dishId").value(catalog.chinese()));
        mvc.perform(get(url).cookie(user.cookie()).param("search","' OR 1=1 --")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(url).cookie(user.cookie()).param("search","🥗".repeat(50))).andExpect(status().isOk());
        mvc.perform(get(url).cookie(user.cookie()).param("search","🥗".repeat(51))).andExpect(status().isBadRequest());
        for(var entry:Map.of("page","-1","size","101","recommended","yes","categories","Invalid").entrySet()) mvc.perform(get(url).cookie(user.cookie()).param(entry.getKey(),entry.getValue())).andExpect(status().isBadRequest());
        mvc.perform(get(url).cookie(user.cookie()).param("categories","Chinese","Chinese")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/menus/2032-02-30").cookie(user.cookie())).andExpect(status().isBadRequest());
    }
    @Test void paginationIsStableByNameThenIdentityAndSupportsMaximumPageSize() throws Exception {
        var fixtures=fixture();var catalog=fixtures.create();var user=new FixtureFactory(sessions,auth).identity(Role.Employee);
        for(int i=0;i<25;i++) fixtures.dish(catalog.date(),"Same name","Pagination fixture",100,"Chinese","Chickpeas",catalog.mediaId(),true,true,1);
        String url="/api/v1/menus/"+catalog.date();
        var all=json.readTree(mvc.perform(get(url).cookie(user.cookie()).param("size","100")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(30)).andReturn().getResponse().getContentAsString()).get("items");
        var paged=new ArrayList<String>();for(int page=0;page<2;page++) {
            var items=json.readTree(mvc.perform(get(url).cookie(user.cookie()).param("page",Integer.toString(page))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");items.forEach(item->paged.add(item.get("dishId").asString()));
        }
        var expected=new ArrayList<String>();all.forEach(item->expected.add(item.get("dishId").asString()));assertEquals(expected,paged);
        var ties=new ArrayList<String>();all.forEach(item->{if(item.get("name").asString().equals("Same name"))ties.add(item.get("dishId").asString());});assertEquals(ties.stream().sorted().toList(),ties);
        mvc.perform(get(url).cookie(user.cookie()).param("page","2147483647")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void publicMediaHasVerifiedBytesAndCannotEscapeItsControlledDirectory() throws Exception {
        var catalog=fixture().create();var asset=media.find(catalog.mediaId()).orElseThrow();
        var response=mvc.perform(get("/media/"+catalog.mediaId())).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("immutable"))).andReturn().getResponse();
        assertArrayEquals(Files.readAllBytes(MEDIA_ROOT.resolve(asset.storageKey())),response.getContentAsByteArray());
        mvc.perform(get("/media/"+UUID.randomUUID())).andExpect(status().isNotFound());
        Path outside=Files.createTempFile("webox-outside-",".jpg"),link=MEDIA_ROOT.resolve(UUID.randomUUID()+".jpg");
        try {
            Files.write(outside,response.getContentAsByteArray());
            jdbc.update("UPDATE media_assets SET storage_key=? WHERE id=?","../"+outside.getFileName(),catalog.mediaId());
            mvc.perform(get("/media/"+catalog.mediaId())).andExpect(status().isNotFound());
            Files.createSymbolicLink(link,outside);jdbc.update("UPDATE media_assets SET storage_key=? WHERE id=?",link.getFileName().toString(),catalog.mediaId());
            mvc.perform(get("/media/"+catalog.mediaId())).andExpect(status().isNotFound());
        } finally { Files.deleteIfExists(link);Files.deleteIfExists(outside);jdbc.update("UPDATE media_assets SET storage_key=? WHERE id=?",asset.storageKey(),catalog.mediaId()); }
    }
}
