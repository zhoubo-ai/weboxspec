package com.webox.catalog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import com.webox.shared.DomainTypes.*;
public final class MenuModels {
    private MenuModels() {}
    public record Option(String optionId,String name,long extraCents,List<Allergen> allergens) { public Option { allergens=List.copyOf(allergens); } }
    public record OptionGroup(String groupId,String name,boolean required,SelectionType selectionMode,List<Option> options) { public OptionGroup { options=List.copyOf(options); } }
    public record Details(String dishId,String name,String description,String imageUrl,long basePriceCents,Category category,String protein,List<Allergen> allergens,SpiceLevel spiceLevel,List<OptionGroup> optionGroups,long dishVersion) {
        public Details { allergens=List.copyOf(allergens);optionGroups=List.copyOf(optionGroups); }
    }
    public record PreferenceMatch(boolean cuisine,boolean spice) {}
    public record MenuDish(String dishId,String name,String description,String imageUrl,long basePriceCents,Category category,String protein,List<Allergen> allergens,SpiceLevel spiceLevel,List<OptionGroup> optionGroups,long dishVersion,int remaining,long inventoryVersion,PreferenceMatch preferenceMatch) {
        public static MenuDish of(Details d,int remaining,long version,PreferenceMatch match) { return new MenuDish(d.dishId(),d.name(),d.description(),d.imageUrl(),d.basePriceCents(),d.category(),d.protein(),d.allergens(),d.spiceLevel(),d.optionGroups(),d.dishVersion(),remaining,version,match); }
    }
    public record MenuPage(LocalDate deliveryDate,Instant serverTime,String businessTimezone,long menuRevision,List<MenuDish> items,int page,int size,long totalElements) {}
}
