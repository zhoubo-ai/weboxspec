package com.webox.shared;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.*;
@RestController
public class HealthController {
    private final JdbcTemplate jdbc;
    public HealthController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/api/v1/health")
    public Map<String,String> health() {
        try { if(!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT 1",Integer.class))) throw new IllegalStateException(); }
        catch(DataAccessException | IllegalStateException e) {
            throw new ApiException(503,"TEMPORARY_UNAVAILABLE","The service is temporarily unavailable. Please retry.",true,Map.of());
        }
        return Map.of("status","UP");
    }
}
