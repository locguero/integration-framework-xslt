package com.framework.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.model.IntegrationEnvelope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Normalises HTTP POST to canonical IntegrationEnvelope. */
@Component
public class HttpEnvelopeNormalizer implements Processor {

    private final ObjectMapper om;
    public HttpEnvelopeNormalizer(ObjectMapper om) { this.om = om; }

    @Override
    public void process(Exchange exchange) throws Exception {
        String body = exchange.getIn().getBody(String.class);
        JsonNode payload = (body != null && !body.isBlank())
                ? om.readTree(body) : om.createObjectNode();

        String cid  = h(exchange, "X-Correlation-Id", UUID.randomUUID().toString());
        String trig = h(exchange, "X-Trigger-Id",    "HTTP");
        String src  = h(exchange, "X-Source-System", "UNKNOWN");
        String ent  = h(exchange, "X-Entity-Type",   "UNKNOWN");
        String op   = h(exchange, "X-Operation",     "CREATE");

        Map<String, String> headers = new HashMap<>();
        exchange.getIn().getHeaders().forEach((k, v) -> { if (v != null) headers.put(k, v.toString()); });

        exchange.getIn().setBody(
                IntegrationEnvelope.ofDefaults(cid, trig, src, ent, op, headers, payload));
        exchange.setProperty("correlationId", cid);
    }

    private String h(Exchange ex, String name, String def) {
        Object v = ex.getIn().getHeader(name); return v != null ? v.toString() : def;
    }
}
