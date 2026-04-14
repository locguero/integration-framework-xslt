package com.framework.cron;

import com.framework.core.model.IntegrationEnvelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Writes CRON-triggered records into the shared {@code request_log} table so
 * they appear in the admin UI alongside HTTP-triggered requests.
 *
 * Uses JdbcTemplate directly (same pattern as {@link WatermarkRepository}) to
 * avoid a circular module dependency — framework-cron cannot import
 * framework-http's RequestLogService.
 */
@Component
public class CronRequestLogger {

    private final JdbcTemplate jdbc;

    public CronRequestLogger(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void received(IntegrationEnvelope env) {
        jdbc.update("""
                INSERT INTO request_log
                  (correlation_id, source_system, entity_type, operation,
                   status, raw_payload, received_at)
                VALUES (?, ?, ?, ?, 'RECEIVED', ?, ?)
                """,
                env.correlationId(),
                env.sourceSystem(),
                env.entityType(),
                env.requestedOperation(),
                env.payload() != null ? env.payload().toString() : null,
                Instant.now().toString());
    }

    @Transactional
    public void accepted(String correlationId, String routingSlip) {
        jdbc.update("""
                UPDATE request_log
                   SET status = 'ACCEPTED',
                       routing_slip = ?,
                       completed_at = ?
                 WHERE correlation_id = ?
                """,
                routingSlip,
                Instant.now().toString(),
                correlationId);
    }

    @Transactional
    public void failed(String correlationId, String errorMessage) {
        jdbc.update("""
                UPDATE request_log
                   SET status = 'FAILED',
                       error_message = ?,
                       completed_at = ?
                 WHERE correlation_id = ?
                """,
                errorMessage,
                Instant.now().toString(),
                correlationId);
    }
}
