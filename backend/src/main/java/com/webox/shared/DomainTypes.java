package com.webox.shared;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
public final class DomainTypes {
    private DomainTypes() {}
    public enum Role { Employee, Admin }
    public enum Category {
        Chinese("Chinese"), Western("Western"), Japanese("Japanese"), LightMeal("Light Meal"), Korean("Korean"), SoutheastAsian("Southeast Asian");
        private final String value;
        Category(String value) { this.value = value; }
        @JsonValue public String value() { return value; }
        @JsonCreator public static Category parse(String value) {
            for (var category : values()) if (category.value.equals(value)) return category;
            throw new IllegalArgumentException("Invalid category.");
        }
    }
    public enum SpiceLevel { None, Mild, Medium, Hot }
    public enum Allergen { Peanuts, Dairy, Egg, Gluten, Soy, Fish, Shellfish }
    public enum Flavor { Light, Moderate, Strong }
    public enum Meal { Lunch, Dinner }
    public enum OrderStatus { Pending, Confirmed, Completed, Cancelled }
    public enum SelectionType { Single, Multiple }
}
