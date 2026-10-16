package com.webox.recommendation;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IntentServiceTest {
    private final IntentService service=new IntentService();private final LocalDate today=LocalDate.of(2026,10,16);
    private IntentService.Extracted extracted(String policy,String from,String through,String evidence,boolean clarify){return new IntentService.Extracted(policy,from,through,evidence,clarify,List.of(),null,null,null,null,List.of(),null);}
    @Test void explicitRelativeAndLongIntervalsUseTheServerBusinessDate() {
        var yesterday=service.normalize("Avoid anything I ate yesterday.",today,extracted("EXCLUDE","2026-10-15","2026-10-15","anything I ate yesterday",false));assertEquals(today.minusDays(1),yesterday.repeatFrom());assertEquals(yesterday.repeatFrom(),yesterday.repeatThrough());
        var longer=service.normalize("No repeats from the last 30 days.",today,extracted("EXCLUDE","2026-09-17","2026-10-16","last 30 days",false));assertEquals(today.minusDays(29),longer.repeatFrom());assertEquals(today,longer.repeatThrough());
        var defaultPeriod=service.normalize("No repeats, please.",today,extracted("EXCLUDE",null,null,"No repeats",false));assertEquals(today.minusDays(6),defaultPeriod.repeatFrom());assertEquals(today,defaultPeriod.repeatThrough());
    }
    @Test void explicitDatesAreInclusiveAndCalendarMonthsAreNotSilentlyReducedToSevenDays() {
        var dates=service.normalize("Avoid meals I ate from 2026-08-01 through 2026-09-30.",today,extracted("EXCLUDE","2026-08-01","2026-09-30","from 2026-08-01 through 2026-09-30",false));assertEquals(LocalDate.of(2026,8,1),dates.repeatFrom());assertEquals(LocalDate.of(2026,9,30),dates.repeatThrough());
        var month=service.normalize("No dishes I had last month.",today,extracted("EXCLUDE","2026-09-01","2026-09-30","last month",false));assertEquals(LocalDate.of(2026,9,1),month.repeatFrom());assertEquals(LocalDate.of(2026,9,30),month.repeatThrough());
    }
    @Test void uncertainUnsupportedOrInventedRangesRequireClarification() {
        for(var value:List.of(extracted("EXCLUDE","2026-10-14","2026-10-14","yesterday",false),extracted("EXCLUDE",null,null,"last year sometime",true),extracted("EXCLUDE","2026-10-01","2026-10-16","not present in the request",false)))assertEquals("INTENT_CLARIFICATION_REQUIRED",assertThrows(AiFailure.class,()->service.normalize("No repeats from yesterday.",today,value)).code());
    }
    @Test void ordinaryPreferencesKeepTheDefaultSevenDayHistorySoft() {
        var value=service.normalize("A mild lunch.",today,extracted("DEFAULT",null,null,null,false));assertFalse(value.hardRepeat());assertNull(value.repeatFrom());
        assertEquals("INTENT_CLARIFICATION_REQUIRED",assertThrows(AiFailure.class,()->service.normalize("No repeats from the last 30 days.",today,extracted("DEFAULT",null,null,null,false))).code());
    }
    @Test void aShortEvidenceQuoteCannotSilentlyDiscardTheRequestedLongerPeriod() {
        var result=service.normalize("No repeats from the last 30 days.",today,extracted("EXCLUDE",null,null,"No repeats",false));assertEquals(today.minusDays(29),result.repeatFrom());
        assertEquals("INTENT_CLARIFICATION_REQUIRED",assertThrows(AiFailure.class,()->service.normalize("No repeats from two years ago.",today,extracted("EXCLUDE",null,null,"No repeats",false))).code());
    }
    @Test void extractionCannotInventAllergiesFromAReferenceToSavedPreferences() {
        var wrong=new IntentService.Extracted("DEFAULT",null,null,null,false,List.of(),null,null,null,null,List.of(com.webox.shared.DomainTypes.Allergen.Dairy),null);
        assertEquals("AI_INVALID_RESPONSE",assertThrows(AiFailure.class,()->service.normalize("Use my saved preferences.",today,wrong)).code());
        assertEquals(java.util.Set.of(com.webox.shared.DomainTypes.Allergen.Dairy),service.normalize("Avoid dairy.",today,wrong).excludedAllergens());
        assertTrue(IntentService.mentionedAllergens("不要奶和花生").containsAll(List.of(com.webox.shared.DomainTypes.Allergen.Dairy,com.webox.shared.DomainTypes.Allergen.Peanuts)));
    }
}
