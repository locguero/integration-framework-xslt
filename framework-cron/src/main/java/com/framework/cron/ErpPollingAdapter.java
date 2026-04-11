package com.framework.cron;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;

/** Fetches pending ERP records since the last watermark. POC returns synthetic data. */
@Component
public class ErpPollingAdapter {

    private static final Logger log = LoggerFactory.getLogger(ErpPollingAdapter.class);

    public List<Map<String, Object>> fetchPendingRecords(Exchange exchange) {
        String wm = exchange.getProperty("WATERMARK", "0", String.class);
        log.info("ERP poll since watermark [{}]", wm);
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("id",      Long.parseLong(wm) + i);
            r.put("orderId", "ORD-" + (Long.parseLong(wm) + i));
            r.put("status",  "PENDING");
            r.put("amount",  100.0 * i);
            records.add(r);
        }
        return records;
    }
}
