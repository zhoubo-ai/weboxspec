package com.webox.recommendation;
import com.webox.auth.*;
import com.webox.catalog.MenuModels.MenuDish;
import com.webox.shared.DomainTypes.*;
import com.webox.support.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc(print=org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class CandidatePolicyIT extends AdminTestSupport {
    @Autowired CandidateService candidates;@Autowired PreferencesService preferences;@Autowired RecommendationValidator validator;
    private Set<String> ids(CandidateService.Prepared value){return value.candidates().stream().map(c->c.dish().dishId()).collect(java.util.stream.Collectors.toSet());}
    @Test void onlyScheduledPublishedInStockDishesAreCandidatesAndMissingPreferencesAreValid() throws Exception {
        var user=browser();var menu=menu();var prepared=candidates.prepare(user.identity().user().userId(),menu.date(),IntentService.Intent.none());
        assertEquals(Set.of(menu.burger(),menu.pasta(),menu.chinese(),menu.japanese()),ids(prepared));assertTrue(prepared.history().isEmpty());assertEquals(0,prepared.preferences().version());
        assertEquals(2650,prepared.candidates().stream().filter(c->c.dish().dishId().equals(menu.pasta())).findFirst().orElseThrow().minimumPriceCents());
    }
    @Test void anyOptionAllergenExcludesTheWholeDishAndFreshPreferencesCanInvalidateIt() throws Exception {
        var user=browser();var menu=menu();String id=user.identity().user().userId();assertTrue(candidates.recheck(id,menu.date(),IntentService.Intent.none(),menu.pasta()).isPresent());
        preferences.replace(id,new PreferencesModels.Write(List.of(Allergen.Dairy),List.of(),null,null,null,null,0L));
        assertEquals(Set.of(menu.chinese(),menu.japanese()),ids(candidates.prepare(id,menu.date(),IntentService.Intent.none())));assertTrue(candidates.recheck(id,menu.date(),IntentService.Intent.none(),menu.pasta()).isEmpty());
        preferences.replace(id,new PreferencesModels.Write(List.of(),List.of(),null,null,null,null,1L));assertTrue(candidates.recheck(id,menu.date(),IntentService.Intent.none(),menu.pasta()).isPresent());
        preferences.replace(id,new PreferencesModels.Write(List.of(Allergen.Egg),List.of(),null,null,null,null,2L));assertEquals(Set.of(menu.pasta(),menu.chinese(),menu.japanese()),ids(candidates.prepare(id,menu.date(),IntentService.Intent.none())));assertTrue(candidates.recheck(id,menu.date(),IntentService.Intent.none(),menu.burger()).isEmpty());
        preferences.replace(id,new PreferencesModels.Write(List.of(),List.of(),null,null,null,null,3L));
        jdbc.update("UPDATE daily_supply SET total_quantity=0,version=version+1 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.pasta());assertTrue(candidates.recheck(id,menu.date(),IntentService.Intent.none(),menu.pasta()).isEmpty());
        jdbc.update("UPDATE daily_supply SET total_quantity=20,version=version+1 WHERE delivery_date=? AND dish_id=?",menu.date(),menu.pasta());jdbc.update("UPDATE dishes SET is_listed=FALSE,version=version+1 WHERE id=?",menu.pasta());assertTrue(candidates.recheck(id,menu.date(),IntentService.Intent.none(),menu.pasta()).isEmpty());
    }
    private void arrange(CatalogFixtures.Menu menu,LocalDate date) {
        jdbc.update("INSERT IGNORE INTO daily_menus VALUES (?,?,?,?)",date,1,menu.adminId(),java.sql.Timestamp.from(TEST_CLOCK.instant()));
        jdbc.update("INSERT INTO daily_supply SELECT ?,dish_id,scheduled,total_quantity,0,0,updated_at FROM daily_supply WHERE delivery_date=?",date,menu.date());
    }
    private String ordered(Browser user,CatalogFixtures.Menu menu,LocalDate date,String dish,String... options) throws Exception {
        TEST_CLOCK.set(date.atTime(8,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());arrange(menu,date);
        var quoted=postJson("/checkout/quotes",user,Map.of("deliveryDate",date.toString(),"meal","Lunch","items",List.of(line(dish,1,1,options))),null);assertEquals(201,quoted.getStatus());
        var placed=postJson("/orders",user,Map.of("quoteId",field(quoted,"quoteId"),"addressText","History fixture office"),UUID.randomUUID().toString());assertEquals(201,placed.getStatus());return field(placed,"orderId");
    }
    @Test void sevenDayHistoryIsSoftButExplicitLongerIntervalsExcludeAllNonCancelledOwnHistory() throws Exception {
        var user=browser();var menu=menu();LocalDate today=menu.date().plusYears(10),target=today.plusDays(60);String id=user.identity().user().userId();
        String older=ordered(user,menu,today.minusDays(10),menu.burger(),menu.options().get("Plain bun"),menu.options().get("Ketchup"));ordered(user,menu,today.minusDays(6),menu.pasta(),menu.options().get("Spaghetti"));
        String cancelled=ordered(user,menu,today.minusDays(2),menu.chinese());assertEquals(200,postJson("/orders/"+cancelled+"/cancel",user,null,null).getStatus());
        ordered(browser(),menu,today.minusDays(1),menu.japanese());TEST_CLOCK.set(today.atTime(8,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());arrange(menu,target);
        var soft=candidates.prepare(id,target,IntentService.Intent.none());assertEquals(Set.of(menu.pasta()),soft.history());assertEquals(4,soft.candidates().size());assertEquals(menu.pasta(),soft.candidates().get(3).dish().dishId());
        var intent=IntentService.Intent.exclude(today.minusDays(29),today);assertEquals(Set.of(menu.chinese(),menu.japanese()),ids(candidates.prepare(id,target,intent)));assertTrue(candidates.recheck(id,target,intent,menu.burger()).isEmpty());assertTrue(candidates.recheck(id,target,intent,menu.chinese()).isPresent());
        assertEquals(200,postJson("/orders/"+older+"/cancel",user,null,null).getStatus());assertTrue(candidates.recheck(id,target,intent,menu.burger()).isPresent());
    }
    @Test void thePromptPayloadCandidateSetIsBoundedAfterHardFiltering() throws Exception {
        var user=browser();var menu=menu();var fixtures=new CatalogFixtures(jdbc,media,MEDIA_ROOT,sessions,auth,manager);
        for(int i=0;i<40;i++)fixtures.dish(menu.date(),"Extra dish "+i,"Fresh vegetables.",1000+i,"Light Meal","Vegetables",menu.mediaId(),true,true,20);
        var prepared=candidates.prepare(user.identity().user().userId(),menu.date(),IntentService.Intent.none());assertEquals(30,prepared.candidates().size());assertEquals(30,ids(prepared).size());assertFalse(ids(prepared).contains(menu.hidden()));assertFalse(ids(prepared).contains(menu.salad()));
    }
    @Test void perCardFactsCannotInventCuisinePriceOrFavoritesAndPreferenceVersionsAreRechecked()throws Exception {
        var user=browser();var menu=menu();String owner=user.identity().user().userId();var intent=IntentService.Intent.none();var offered=candidates.prepare(owner,menu.date(),intent);
        assertTrue(validator.current(owner,menu.date(),intent,offered,new RecommendationModels.Generated(menu.pasta(),"A Western choice with tomato sauce.")).isPresent());
        assertTrue(validator.current(owner,menu.date(),intent,offered,new RecommendationModels.Generated(menu.chinese(),"This Chinese salad offers a light meal with a plant-based dressing.")).isPresent());
        assertTrue(validator.current(owner,menu.date(),intent,offered,new RecommendationModels.Generated(menu.chinese(),"This Chinese salad offers variety from the Japanese option.")).isPresent());
        for(String reason:List.of("A Japanese pasta dish.","A favorite cuisine for you.","This costs 1 yuan; options can change the price.","This costs 26.50 yuan."))assertThrows(AiFailure.class,()->validator.current(owner,menu.date(),intent,offered,new RecommendationModels.Generated(menu.pasta(),reason)));
        assertTrue(validator.current(owner,menu.date(),intent,offered,new RecommendationModels.Generated(menu.pasta(),"From 26.50 yuan; options can change the price.")).isPresent());
        preferences.replace(owner,new PreferencesModels.Write(List.of(),List.of(Category.Japanese),null,null,null,null,0L));assertTrue(validator.current(owner,menu.date(),intent,offered,new RecommendationModels.Generated(menu.pasta(),"A Western choice.")).isEmpty());
    }
    @Test void newlyPlacedHistoryIsReadAgainBeforeIssuingEachCard()throws Exception {
        var user=browser();var menu=menu();String owner=user.identity().user().userId();LocalDate today=menu.date().plusYears(10),target=today.plusDays(60);TEST_CLOCK.set(today.atTime(8,0).atZone(com.webox.shared.BusinessClock.ZONE).toInstant());arrange(menu,target);
        var intent=IntentService.Intent.none();var offered=candidates.prepare(owner,target,intent);assertTrue(offered.history().isEmpty());
        ordered(user,menu,today,menu.burger(),menu.options().get("Plain bun"),menu.options().get("Ketchup"));assertTrue(validator.current(owner,target,intent,offered,new RecommendationModels.Generated(menu.burger(),"A new choice for your meal.")).isEmpty());assertTrue(validator.current(owner,target,intent,offered,new RecommendationModels.Generated(menu.pasta(),"A choice for your meal.")).isPresent());
    }
}
