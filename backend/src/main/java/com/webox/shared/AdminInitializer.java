package com.webox.shared;
import com.webox.auth.AuthService;
import com.webox.auth.UserIdentity;
import org.springframework.stereotype.Component;
@Component
public class AdminInitializer {
    private final AuthService auth;
    public AdminInitializer(AuthService auth) { this.auth=auth; }
    public UserIdentity initialize(String email,String initialPassword) { return auth.initializeAdmin(email,initialPassword); }
}
