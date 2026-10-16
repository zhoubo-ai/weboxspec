package com.webox.auth;
import com.webox.shared.*;
import com.webox.shared.DomainTypes.Role;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class AuthService {
    // Argon2id: 19 MiB memory, 2 iterations, parallelism 1, 16-byte salt, 32-byte hash.
    private final Argon2PasswordEncoder passwords=new Argon2PasswordEncoder(16,32,1,19456,2);
    private final String dummyHash=passwords.encode(UUID.randomUUID().toString());
    private final UserRepository users;private final BusinessClock clock;private final Validator validator;
    public AuthService(UserRepository users,BusinessClock clock,Validator validator) { this.users=users;this.clock=clock;this.validator=validator; }
    record EmailInput(@Email String value) {}
    public String email(String value) {
        String clean;
        try { clean=ValueRules.requiredText(value,200); } catch(IllegalArgumentException e) { throw ApiException.validation("email","Enter a valid email address of at most 200 characters."); }
        if(!validator.validate(new EmailInput(clean)).isEmpty()) throw ApiException.validation("email","Enter a valid email address.");
        return clean;
    }
    private String password(String value) {
        try { return ValueRules.password(value); } catch(IllegalArgumentException e) { throw ApiException.validation("password","Password must contain 8 to 128 characters, including a letter and a number."); }
    }
    public UserIdentity register(String email,String password) { return create(email(email),password(password),Role.Employee); }
    private UserIdentity create(String email,String password,Role role) {
        var row=new UserRepository.UserRow(UUID.randomUUID().toString(),email,email.toLowerCase(Locale.ROOT),passwords.encode(password),role);
        try { users.insert(row,clock.now()); }
        catch(DuplicateKeyException e) { throw new ApiException(409,"EMAIL_ALREADY_REGISTERED","This email is already registered. Please sign in."); }
        return row.identity();
    }
    public UserIdentity login(String input,String rawPassword) {
        String email=email(input);String password=password(rawPassword);
        var found=users.byEmail(email.toLowerCase(Locale.ROOT));
        boolean valid=passwords.matches(password,found.map(UserRepository.UserRow::passwordHash).orElse(dummyHash));
        if(!valid || found.isEmpty()) throw new ApiException(401,"INVALID_CREDENTIALS","Email or password is incorrect.");
        return found.get().identity();
    }
    public UserIdentity initializeAdmin(String input,String initialPassword) {
        String email=email(input);var existing=users.byEmail(email.toLowerCase(Locale.ROOT));
        if(existing.isPresent()) {
            if(existing.get().role()!=Role.Admin) throw new IllegalStateException("The configured administrator email belongs to an employee; refusing to elevate it.");
            return existing.get().identity();
        }
        return create(email,password(initialPassword),Role.Admin);
    }
}
