package com.webox.auth;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.webox.shared.DomainTypes.*;
public final class PreferencesModels {
    private PreferencesModels() {}
    public record Preferences(List<Allergen> allergens,List<Category> cuisines,SpiceLevel spiceLevel,Flavor flavor,Long budgetMinCents,Long budgetMaxCents,long version) {
        public Preferences { allergens=List.copyOf(allergens);cuisines=List.copyOf(cuisines); }
        public static Preferences empty() { return new Preferences(List.of(),List.of(),null,null,null,null,0); }
    }
    public record Write(
        @JsonProperty(value="allergens",required=true) List<Allergen> allergens,
        @JsonProperty(value="cuisines",required=true) List<Category> cuisines,
        @JsonProperty(value="spiceLevel",required=true) SpiceLevel spiceLevel,
        @JsonProperty(value="flavor",required=true) Flavor flavor,
        @JsonProperty(value="budgetMinCents",required=true) Long budgetMinCents,
        @JsonProperty(value="budgetMaxCents",required=true) Long budgetMaxCents,
        @JsonProperty(value="expectedVersion",required=true) Long expectedVersion) {}
}
