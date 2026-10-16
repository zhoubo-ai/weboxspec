package com.webox.auth;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
/** Read-only repository lookup; no save, HTTP request or last-access update. */
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@Component
public class SessionValidity {
    private final SessionRepository<?> sessions;
    public SessionValidity(SessionRepository<?> sessions) { this.sessions=sessions; }
    public boolean active(String sessionId) {
        if(sessionId==null) return false;
        var session=sessions.findById(sessionId);
        if (session == null || session.isExpired()) return false;
        Object stored = session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (!(stored instanceof org.springframework.security.core.context.SecurityContext context)) return false;
        var auth = context.getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserIdentity;
    }
}
