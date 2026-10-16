package com.webox.shared;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
/** Same application entry point; only the explicit seed profile runs this once. */
@Component @Profile("seed")
public class SeedApplication implements CommandLineRunner {
    private final AdminInitializer admins;private final MenuSeeder menus;private final Environment environment;private final ConfigurableApplicationContext context;
    public SeedApplication(AdminInitializer admins,MenuSeeder menus,Environment environment,ConfigurableApplicationContext context) { this.admins=admins;this.menus=menus;this.environment=environment;this.context=context; }
    @Override public void run(String... args) {
        var admin=admins.initialize(environment.getProperty("ADMIN_EMAIL"),environment.getProperty("ADMIN_INITIAL_PASSWORD"));
        menus.initialize(admin);
        context.close();
    }
}
