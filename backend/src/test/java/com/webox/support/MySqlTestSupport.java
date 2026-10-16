package com.webox.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

/** A disposable real database; never connects to the development Compose volume. */
@ActiveProfiles("test")
@Import(MySqlTestSupport.TimeConfiguration.class)
public abstract class MySqlTestSupport {
    // Digest identifies MySQL 8.4.11. Testcontainers expects repository@digest without a tag.
    public static final String MYSQL_IMAGE = "mysql@sha256:24989179f58856db763f418010c1169fecc944e3ec1ea7e921572d83eb070f70";
    public static final Instant INITIAL_TIME = Instant.parse("2026-10-16T01:00:00Z");
    protected static final MutableClock TEST_CLOCK = new MutableClock(INITIAL_TIME, ZoneOffset.UTC);
    protected static final Path MEDIA_ROOT = temporaryMedia();
    protected static final MySQLContainer MYSQL = database();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MYSQL.stop();
            try (var paths = Files.walk(MEDIA_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { /* Temporary files only. */ }
                });
            } catch (IOException ignored) { /* The OS may already have removed the temporary directory. */ }
        }, "webox-test-cleanup"));
    }

    @org.junit.jupiter.api.BeforeEach
    void restoreTestClock() { TEST_CLOCK.set(INITIAL_TIME); }

    private static Path temporaryMedia() {
        try { return Files.createTempDirectory("webox-test-media-"); }
        catch (IOException e) { throw new IllegalStateException("Cannot create isolated test media directory", e); }
    }

    private static MySQLContainer database() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new IllegalStateException("Docker is required for core MySQL integration tests; tests cannot be skipped.");
        }
        var mysql = new MySQLContainer(MYSQL_IMAGE)
                .withDatabaseName("webox_test")
                .withUsername("webox_test")
                .withPassword(UUID.randomUUID().toString())
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci", "--default-time-zone=+00:00");
        mysql.start();
        return mysql;
    }

    protected static String jdbcUrl() {
        String base = MYSQL.getJdbcUrl();
        return base + (base.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MySqlTestSupport::jdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("webox.media-root", () -> MEDIA_ROOT.toString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class TimeConfiguration {
        @Bean @Primary Clock testClock() { return TEST_CLOCK; }
    }

    public static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;
        private final ThreadLocal<Instant> requestTime;
        public MutableClock(Instant instant, ZoneId zone) { this(new AtomicReference<>(instant), zone, new ThreadLocal<>()); }
        private MutableClock(AtomicReference<Instant> current, ZoneId zone, ThreadLocal<Instant> requestTime) { this.current = current; this.zone = zone;this.requestTime=requestTime; }
        public void set(Instant instant) { current.set(instant); }
        public void requestTime(Instant instant) { if(instant==null) requestTime.remove();else requestTime.set(instant); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(current, zone, requestTime); }
        @Override public Instant instant() { return requestTime.get()==null?current.get():requestTime.get(); }
    }
}
