package com.webox.auth;
import com.webox.WeBoxApplication;
import com.webox.support.MySqlTestSupport;
import java.util.UUID;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.junit.jupiter.api.Assertions.*;
class SeedApplicationIT extends MySqlTestSupport {
    @Test void explicitSeedRunsWithoutHttpAndExitsWithoutResettingAdmin() throws Exception {
        String email=UUID.randomUUID()+"@example.com";String password="SeedExample123";
        String firstHash=null;
        for(int attempt=0;attempt<2;attempt++) {
            // These arguments are in-process only, never shell arguments or logs.
            try(var context=new SpringApplicationBuilder(WeBoxApplication.class).profiles("seed","test")
                    .run("--spring.datasource.url="+jdbcUrl(),"--spring.datasource.username="+MYSQL.getUsername(),
                        "--spring.datasource.password="+MYSQL.getPassword(),"--webox.media-root="+MEDIA_ROOT,
                        "--ADMIN_EMAIL="+email,"--ADMIN_INITIAL_PASSWORD="+(attempt==0?password:"IgnoredChanged123"))) {
                assertFalse(context.isActive(),"The seed runner must close its context after completing.");
            }
            try(var connection=DriverManager.getConnection(jdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
                var query=connection.prepareStatement("SELECT password_hash,role FROM users WHERE normalized_email=?")) {
                query.setString(1,email);try(var rows=query.executeQuery()) {
                    assertTrue(rows.next());String hash=rows.getString(1);assertEquals("Admin",rows.getString(2));
                    if(attempt==0) firstHash=hash;else assertEquals(firstHash,hash);assertFalse(rows.next());
                }
            }
        }
    }
}
