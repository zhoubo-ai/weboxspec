package com.webox.shared;
import java.time.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.webox.shared.DomainTypes.*;
import static org.junit.jupiter.api.Assertions.*;
class ValueRulesTest {
    @Test void sharedSamplesMatchMoneyAndUnicodeContract() throws Exception {
        var json=JsonMapper.builder().build().readTree(getClass().getResourceAsStream("/fixtures/values.json"));
        for (var row:json.path("money")) assertEquals(row.path("display").asText(), ValueRules.money(row.path("cents").asLong()));
        for (var row:json.path("texts")) {
            String normalized=ValueRules.requiredText(row.path("input").asText(), 200);
            assertEquals(row.path("normalized").asText(), normalized);
            assertEquals(row.path("codePoints").asInt(), ValueRules.codePoints(normalized));
        }
        assertEquals(json.path("password").asText(), ValueRules.password(json.path("password").asText()));
    }
    @Test void arithmeticNeverRoundsOverflowsOrWraps() {
        assertEquals(ValueRules.MAX_SAFE_INTEGER, ValueRules.add(ValueRules.MAX_SAFE_INTEGER,0));
        assertEquals(5900, ValueRules.multiply(2950,2));
        assertEquals(1,ValueRules.nextVersion(0));
        assertThrows(IllegalArgumentException.class,()->ValueRules.safeInteger(-1));
        assertThrows(IllegalArgumentException.class,()->ValueRules.add(ValueRules.MAX_SAFE_INTEGER,1));
        assertThrows(IllegalArgumentException.class,()->ValueRules.multiply(ValueRules.MAX_SAFE_INTEGER,Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->ValueRules.nextVersion(ValueRules.MAX_SAFE_INTEGER));
    }
    @Test void textAndPasswordBoundariesUseCodePointsWithoutTruncation() {
        assertEquals("🍜".repeat(200),ValueRules.requiredText("🍜".repeat(200),200));
        assertThrows(IllegalArgumentException.class,()->ValueRules.requiredText("🍜".repeat(201),200));
        assertThrows(IllegalArgumentException.class,()->ValueRules.requiredText(" \n\u3000",200));
        assertThrows(IllegalArgumentException.class,()->ValueRules.requiredText("\uD800",200));
        assertEquals("A1"+"🍜".repeat(126),ValueRules.password("A1"+"🍜".repeat(126)));
        for (String invalid:new String[]{"A123456", "abcdefgh", "12345678", "A1"+"x".repeat(127)})
            assertThrows(IllegalArgumentException.class,()->ValueRules.password(invalid));
    }
    @Test void operatingDatesAndCutoffsDoNotDependOnMachineTimezone() {
        var clock=new BusinessClock(Clock.fixed(Instant.parse("2026-10-16T16:00:00Z"),ZoneOffset.ofHours(-5)));
        assertEquals(LocalDate.of(2026,10,17),clock.today());
        assertEquals(Instant.parse("2026-10-17T02:00:00Z"),clock.cutoff(clock.today(),Meal.Lunch));
        assertEquals(Instant.parse("2026-10-17T07:00:00Z"),clock.cutoff(clock.today(),Meal.Dinner));
        assertEquals(Instant.parse("2026-10-16T16:00:00Z"),clock.now());
    }
    @Test void enumWireValuesAreExact() {
        var mapper=JsonMapper.builder().build();
        assertEquals("\"Light Meal\"",mapper.writeValueAsString(Category.LightMeal));
        assertEquals(Category.SoutheastAsian,mapper.readValue("\"Southeast Asian\"",Category.class));
        assertThrows(IllegalArgumentException.class,()->Category.parse("LightMeal"));
    }
}
