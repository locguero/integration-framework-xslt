package com.framework.cron;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

/** Persists the poll high-watermark. Updated atomically with delivery confirmation. */
@Repository
public class WatermarkRepository {

    private final JdbcTemplate jdbc;
    public WatermarkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String getWatermark(String sourceId) {
        try {
            return jdbc.queryForObject(
                    "SELECT last_value FROM poll_watermark WHERE source_id=?", String.class, sourceId);
        } catch (Exception e) { return "0"; }
    }

    @Transactional
    public void updateWatermark(String sourceId, String newValue) {
        int n = jdbc.update("UPDATE poll_watermark SET last_value=?,updated_at=? WHERE source_id=?",
                newValue, Instant.now().toString(), sourceId);
        if (n == 0) jdbc.update(
                "INSERT INTO poll_watermark(source_id,last_value,updated_at) VALUES(?,?,?)",
                sourceId, newValue, Instant.now().toString());
    }
}
