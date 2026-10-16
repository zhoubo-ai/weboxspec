package com.webox.auth;
import java.io.Serializable;
import java.security.Principal;
import com.webox.shared.DomainTypes.Role;
/** Safe identity carried by the server session; never stores a password or token. */
public record UserIdentity(String userId,String email,Role role) implements Principal,Serializable {
    @com.fasterxml.jackson.annotation.JsonIgnore @Override public String getName() { return userId; }
}
