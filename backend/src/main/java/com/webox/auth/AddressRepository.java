package com.webox.auth;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import com.webox.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Repository
public class AddressRepository {
    private final JdbcTemplate jdbc;
    public AddressRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Address(String addressId,String text,Instant lastUsedAt) {}
    public List<Address> list(String user) { return jdbc.query("SELECT id,address_text,last_used_at FROM delivery_addresses WHERE user_id=? ORDER BY last_used_at DESC,id",(rs,n)->new Address(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant()),user); }
    public String resolve(String user,String id) { return jdbc.query("SELECT address_text FROM delivery_addresses WHERE user_id=? AND id=?",(rs,n)->rs.getString(1),user,id).stream().findFirst().orElseThrow(()->new ApiException(404,"NOT_FOUND","This address was not found.")); }
    public void use(String user,String address,Instant now) {
        if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new IllegalStateException("Saving an address requires the successful order transaction.");
        jdbc.update("INSERT INTO delivery_addresses(id,user_id,address_text,created_at,last_used_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE last_used_at=?",UUID.randomUUID().toString(),user,address,Timestamp.from(now),Timestamp.from(now),Timestamp.from(now));
    }
}
