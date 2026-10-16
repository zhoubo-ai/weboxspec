package com.webox.support;

import java.nio.file.Files;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MySqlEnvironmentIT extends MySqlTestSupport {
    @Test
    void realMysqlSupportsTheRequiredCharsetTimezoneAndRollback() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT VERSION(), @@session.time_zone, @@character_set_server")) {
                assertTrue(result.next());
                assertTrue(result.getString(1).startsWith("8.4."));
                assertEquals("+00:00", result.getString(2));
                assertEquals("utf8mb4", result.getString(3));
            }
            statement.execute("CREATE TABLE environment_probe (id INT PRIMARY KEY, value VARCHAR(40)) ENGINE=InnoDB");
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("INSERT INTO environment_probe VALUES (?, ?)")) {
                insert.setInt(1, 1); insert.setString(2, "上海 🍜"); insert.executeUpdate();
            }
            connection.rollback();
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM environment_probe")) {
                assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
            }
            connection.setAutoCommit(true);
            statement.execute("DROP TABLE environment_probe");
        }
        assertTrue(Files.isDirectory(MEDIA_ROOT));
        assertEquals(INITIAL_TIME, TEST_CLOCK.instant());
    }
}
