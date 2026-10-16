package com.webox.auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;private final HttpSessionSecurityContextRepository contexts;private final HttpSessionCsrfTokenRepository csrf;
    public AuthController(AuthService service,HttpSessionSecurityContextRepository contexts,HttpSessionCsrfTokenRepository csrf) { this.service=service;this.contexts=contexts;this.csrf=csrf; }
    public record Credentials(String email,String password) { @Override public String toString() { return "Credentials[redacted]"; } }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public UserIdentity register(@RequestBody Credentials body) { return service.register(body.email(),body.password()); }
    @PostMapping("/login")
    public UserIdentity login(@RequestBody Credentials body,HttpServletRequest request,HttpServletResponse response) {
        var user=service.login(body.email(),body.password());
        var authentication=UsernamePasswordAuthenticationToken.authenticated(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role().name())));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(authentication,request,response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);contexts.saveContext(context,request,response);
        return user;
    }
    @GetMapping("/me") public UserIdentity me(@AuthenticationPrincipal UserIdentity user) { return user; }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        csrf.saveToken(null,request,response);
        new SecurityContextLogoutHandler().logout(request,response,authentication);
    }
}
