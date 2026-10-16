package com.webox.load;

import com.webox.WeBoxApplication;
import com.webox.auth.AuthService;
import com.webox.media.MediaRepository;
import com.webox.shared.DomainTypes.Role;
import com.webox.support.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/** Separate application JVM, disposable MySQL and media; no fixture HTTP endpoints or development DB. */
public class LoadFixture extends MySqlTestSupport {
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("A private handoff path is required.");
        Path state=Path.of(args[0]);
        var context=new SpringApplicationBuilder(WeBoxApplication.class).run(
            "--server.address=127.0.0.1","--server.port=0","--spring.profiles.active=load-fixture",
            "--spring.datasource.url="+jdbcUrl(),"--spring.datasource.username="+MYSQL.getUsername(),
            "--spring.datasource.password="+MYSQL.getPassword(),"--webox.media-root="+MEDIA_ROOT,
            "--server.servlet.session.cookie.secure=false","--management.endpoints.web.exposure.include=health,metrics",
            "--webox.rate-limit.enabled=true","--webox.rate-limit.auth-capacity=240","--webox.rate-limit.auth-per-minute=300");
        var jdbc=context.getBean(JdbcTemplate.class);var sessions=context.getBean(JdbcIndexedSessionRepository.class);
        var auth=context.getBean(AuthService.class);var users=new FixtureFactory(sessions,auth);
        var catalog=new CatalogFixtures(jdbc,context.getBean(MediaRepository.class),MEDIA_ROOT,sessions,auth,context.getBean(PlatformTransactionManager.class));
        var menu=catalog.create();var dishes=new ArrayList<String>();
        for(int i=0;i<493;i++)dishes.add(catalog.dish(menu.date(),String.format("Load Dish %03d",i),"Fresh greens and rice for the performance fixture.",2000,"Light Meal","Chickpeas",menu.mediaId(),true,true,10000));
        jdbc.update("UPDATE dishes SET is_listed=TRUE");jdbc.update("UPDATE daily_supply SET scheduled=TRUE,total_quantity=10000,allocated_quantity=0");
        var employees=new ArrayList<Map<String,Object>>();
        for(int i=0;i<200;i++){var user=users.identity(Role.Employee);employees.add(Map.of("cookie",user.sessionId(),"dishId",dishes.get(i),"search",String.format("Load Dish %03d",i)));}
        var admin=users.identity(Role.Admin);var browser=users.identity(Role.Employee);var writer=users.identity(Role.Employee);
        int port=((WebServerApplicationContext)context).getWebServer().getPort();
        var handoff=new LinkedHashMap<String,Object>();handoff.put("apiOrigin","http://127.0.0.1:"+port);handoff.put("date",menu.date().toString());handoff.put("employees",employees);handoff.put("adminCookie",admin.sessionId());handoff.put("browserCookie",browser.sessionId());handoff.put("writerCookie",writer.sessionId());handoff.put("browserDish",dishes.get(492));handoff.put("browserDishName","Load Dish 492");handoff.put("dishCount",jdbc.queryForObject("SELECT COUNT(*) FROM dishes",Long.class));
        Files.setPosixFilePermissions(state,PosixFilePermissions.fromString("rw-------"));
        Files.writeString(state,context.getBean(JsonMapper.class).writeValueAsString(handoff));
        // Spring's non-daemon server threads retain the process; launcher owns graceful shutdown and cleanup.
    }
}
