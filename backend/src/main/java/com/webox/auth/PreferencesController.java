package com.webox.auth;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/me/preferences")
public class PreferencesController {
    private final PreferencesService preferences;
    public PreferencesController(PreferencesService preferences) {this.preferences=preferences;}
    @GetMapping public PreferencesModels.Preferences get(@AuthenticationPrincipal UserIdentity user) {return preferences.get(user.userId());}
    @PutMapping public PreferencesModels.Preferences replace(@AuthenticationPrincipal UserIdentity user,@RequestBody PreferencesModels.Write body) {return preferences.replace(user.userId(),body);}
}
