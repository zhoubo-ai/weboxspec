package com.webox.auth;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import com.webox.shared.DomainTypes.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<UserRow> ROW=(rs,n)->new UserRow(rs.getString("id"),rs.getString("email"),rs.getString("normalized_email"),rs.getString("password_hash"),Role.valueOf(rs.getString("role")));
    public UserRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Optional<UserRow> byEmail(String normalized) { return jdbc.query("SELECT id,email,normalized_email,password_hash,role FROM users WHERE normalized_email=?",ROW,normalized).stream().findFirst(); }
    public Optional<UserRow> byId(String id) { return jdbc.query("SELECT id,email,normalized_email,password_hash,role FROM users WHERE id=?",ROW,id).stream().findFirst(); }
    public Optional<UserRow> lockOwner(String id) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Owner locking requires a transaction.");
        return jdbc.query("SELECT id,email,normalized_email,password_hash,role FROM users WHERE id=? FOR UPDATE",ROW,id).stream().findFirst();
    }
    public void insert(UserRow user,Instant now) {
        jdbc.update("INSERT INTO users(id,email,normalized_email,password_hash,role,created_at,updated_at) VALUES (?,?,?,?,?,?,?)",user.id(),user.email(),user.normalizedEmail(),user.passwordHash(),user.role().name(),Timestamp.from(now),Timestamp.from(now));
    }
    public record UserRow(String id,String email,String normalizedEmail,String passwordHash,Role role) {
        public UserIdentity identity() { return new UserIdentity(id,email,role); }
        @Override public String toString() { return "UserRow[redacted]"; }
    }
}
