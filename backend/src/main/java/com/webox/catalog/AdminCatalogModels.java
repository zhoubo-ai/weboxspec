package com.webox.catalog;
import java.util.List;
import java.time.LocalDate;
import com.webox.shared.DomainTypes.*;
import static com.webox.catalog.MenuModels.*;
public final class AdminCatalogModels {
    private AdminCatalogModels() {}
    public record OptionCreate(String name,Long extraCents,List<Allergen> allergens) {}
    public record GroupCreate(String name,Boolean required,SelectionType selectionMode,List<OptionCreate> options) {}
    public record DishCreate(String name,String description,Long basePriceCents,Category category,String protein,List<Allergen> allergens,SpiceLevel spiceLevel,String mediaId,List<GroupCreate> optionGroups) {}
    public record OptionWrite(@tools.jackson.databind.annotation.JsonDeserialize(using=com.webox.shared.OptionalIdDeserializer.class) String optionId,String name,Long extraCents,List<Allergen> allergens) {}
    public record GroupWrite(@tools.jackson.databind.annotation.JsonDeserialize(using=com.webox.shared.OptionalIdDeserializer.class) String groupId,String name,Boolean required,SelectionType selectionMode,List<OptionWrite> options) {}
    public record DishUpdate(String name,String description,Long basePriceCents,Category category,String protein,List<Allergen> allergens,SpiceLevel spiceLevel,String mediaId,List<GroupWrite> optionGroups,Long expectedVersion) {}
    public record Publication(Boolean published,Long expectedVersion) {}
    public record AdminDish(String dishId,String name,String description,String imageUrl,long basePriceCents,Category category,String protein,List<Allergen> allergens,SpiceLevel spiceLevel,List<OptionGroup> optionGroups,long dishVersion,boolean published,String mediaId) {
        public static AdminDish of(Details d,boolean published,String mediaId) {return new AdminDish(d.dishId(),d.name(),d.description(),d.imageUrl(),d.basePriceCents(),d.category(),d.protein(),d.allergens(),d.spiceLevel(),d.optionGroups(),d.dishVersion(),published,mediaId);}
    }
    public record AdminDishPage(List<AdminDish> items,int page,int size,long totalElements) {}
    public record Supply(String dishId,String dishName,boolean scheduled,int totalQuantity,int allocatedQuantity,int remaining,long version) {}
    public record DailyMenu(LocalDate deliveryDate,long revision,List<Supply> items) {}
    public record SupplyWrite(String dishId,Integer totalQuantity) {}
    public record DailyMenuWrite(Long expectedRevision,List<SupplyWrite> items) {}
}
