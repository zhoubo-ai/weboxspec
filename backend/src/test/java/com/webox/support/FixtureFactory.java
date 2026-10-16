package com.webox.support;
import java.util.List;
import java.util.UUID;
import com.webox.auth.UserIdentity;
import com.webox.shared.DomainTypes.Role;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.Cookie;
/** Isolated database session fixtures. Business user rows are added with the auth story. */
public final class FixtureFactory {
    private final SessionRepository<?> sessions;
    private final com.webox.auth.AuthService auth;
    public FixtureFactory(SessionRepository<?> sessions, com.webox.auth.AuthService auth) { this.sessions=sessions;this.auth=auth; }
    public IdentitySession identity(Role role) { return create(sessions,role,auth); }
    private static <S extends Session> IdentitySession create(SessionRepository<S> sessions,Role role,com.webox.auth.AuthService auth) {
        String id=UUID.randomUUID().toString();
        var user=role==Role.Admin ? auth.initializeAdmin(id+"@example.com","Fixture123"+id) : auth.register(id+"@example.com","Fixture123"+id);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,List.of(new SimpleGrantedAuthority("ROLE_"+role.name()))));
        var session=sessions.createSession();session.setAttribute("SPRING_SECURITY_CONTEXT",context);sessions.save(session);
        return new IdentitySession(user,session.getId());
    }
    public record IdentitySession(UserIdentity user,String sessionId) {
        public Cookie cookie() { return new Cookie("WEBOXSESSION",sessionId); }
    }
}
