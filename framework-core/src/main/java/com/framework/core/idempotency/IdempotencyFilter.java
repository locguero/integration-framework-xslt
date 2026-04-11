package com.framework.core.idempotency;

import com.framework.core.model.IntegrationEnvelope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency gate. Uses Redis SETNX (setIfAbsent) for an atomic check.
 * Sets IDEMPOTENT_SKIP=true on duplicate correlationIds.
 * TTL 24h covers Kafka at-least-once redelivery and RFC 9110 HTTP idempotency.
 */
@Component
public class IdempotencyFilter implements Processor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);
    public  static final String PROP_SKIP  = "IDEMPOTENT_SKIP";
    private static final String KEY_PREFIX = "idem:";
    private static final long   TTL_HOURS  = 24;

    private final StringRedisTemplate redis;
    public IdempotencyFilter(StringRedisTemplate redis) { this.redis = redis; }

    @Override
    public void process(Exchange exchange) {
        IntegrationEnvelope env = exchange.getIn().getBody(IntegrationEnvelope.class);
        if (env == null || env.correlationId() == null) return;
        String key = KEY_PREFIX + env.correlationId();
        Boolean isNew = redis.opsForValue().setIfAbsent(key, "PROCESSING", TTL_HOURS, TimeUnit.HOURS);
        exchange.setProperty(PROP_SKIP, Boolean.FALSE.equals(isNew));
        if (Boolean.FALSE.equals(isNew))
            log.info("Duplicate correlationId [{}] – skipping", env.correlationId());
    }

    public void markCompleted(String cid) {
        redis.opsForValue().set(KEY_PREFIX + cid, "COMPLETED", TTL_HOURS, TimeUnit.HOURS);
    }

    public void markFailed(String cid) {
        redis.opsForValue().set(KEY_PREFIX + cid, "FAILED", TTL_HOURS, TimeUnit.HOURS);
    }
}
