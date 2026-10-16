package com.webox.recommendation;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.webox.shared.DomainTypes.Meal;
import java.util.List;
public final class RecommendationModels {
    private RecommendationModels(){}
    public record Request(@JsonProperty(required=true) String deliveryDate,@JsonProperty(required=true) Meal meal,@JsonProperty(required=true) String prompt){}
    public record Generated(@JsonProperty(required=true) String dishId,@JsonProperty(required=true) String reason){}
    public record Output(@JsonProperty(required=true) List<Generated> recommendations){}
}
