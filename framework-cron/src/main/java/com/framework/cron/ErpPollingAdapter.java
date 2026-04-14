package com.framework.cron;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Fetches pending ERP records since the last watermark for a given request type.
 * The active {@link CronRequestType} is expected on the exchange property
 * {@code REQUEST_TYPE} and the high-watermark on {@code WATERMARK}.
 * POC implementation returns synthetic data; replace with a real HTTP/DB call.
 */
@Component
public class ErpPollingAdapter {

    private static final Logger log = LoggerFactory.getLogger(ErpPollingAdapter.class);

    public List<Map<String, Object>> fetchPendingRecords(Exchange exchange) {
        String wm = exchange.getProperty("WATERMARK", "0", String.class);
        CronRequestType type = exchange.getProperty("REQUEST_TYPE", CronRequestType.class);

        String source    = type != null ? type.getSourceSystem() : "ERP";
        String entity    = type != null ? type.getEntityType()   : "ORDER";
        String operation = type != null ? type.getOperation()    : "CREATE";

        log.info("ERP poll [{}:{}/{}] since watermark [{}]", source, entity, operation, wm);

        // ── Replace the block below with a real API / DB query ─────────────
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("id",        Long.parseLong(wm) + i);
            r.put("orderId",   "ORD-" + (Long.parseLong(wm) + i));
            r.put("status",    "PENDING");
            r.put("amount",    100.0 * i);
            r.put("operation", operation);
            records.add(r);
        }
        // ────────────────────────────────────────────────────────────────────
        return records;
    }
}
