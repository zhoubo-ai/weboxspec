package com.webox.recommendation;
import com.webox.auth.UserIdentity;
import com.webox.shared.ValueRules;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service){this.service=service;}
    @PostMapping("/stream")
    public void recommend(@AuthenticationPrincipal UserIdentity user,@RequestBody RecommendationModels.Request body,HttpServletRequest request,HttpServletResponse response)throws IOException {
        var date=ValueRules.date(body.deliveryDate());String prompt=ValueRules.requiredText(body.prompt(),1000);
        if(body.meal()==null)throw new IllegalArgumentException("Select a meal.");
        service.start(user.userId(),date,body.meal(),prompt,request,response);
    }
}
