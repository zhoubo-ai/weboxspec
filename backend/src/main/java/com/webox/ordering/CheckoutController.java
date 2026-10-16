package com.webox.ordering;
import com.webox.auth.UserIdentity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final QuoteService quotes;
    public CheckoutController(QuoteService quotes) { this.quotes=quotes; }
    @PostMapping("/quotes") @ResponseStatus(HttpStatus.CREATED)
    public OrderingModels.Quote quote(@AuthenticationPrincipal UserIdentity user,@RequestBody OrderingModels.QuoteRequest request) { return quotes.create(user.userId(),request); }
}
