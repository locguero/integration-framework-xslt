package com.framework.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.idempotency.IdempotencyFilter;
import com.framework.core.model.IntegrationEnvelope;
import com.framework.core.routing.RoutingSlipExecutor;
import com.framework.core.security.SecurityContextExtractor;
import com.framework.xslt.XsltRoutingProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Trigger Route. At-Least-Once delivery.
 * SEDA bounded queue provides backpressure.
 * Poison pills routed to DLT after maxRedeliveries.
 */
@Component
public class KafkaTriggerRoute extends RouteBuilder {

    private final IdempotencyFilter        idempotency;
    private final SecurityContextExtractor security;
    private final XsltRoutingProcessor     routing;
    private final RoutingSlipExecutor      executor;
    private final ObjectMapper             objectMapper;

    public KafkaTriggerRoute(IdempotencyFilter idempotency, SecurityContextExtractor security,
                              XsltRoutingProcessor routing, RoutingSlipExecutor executor,
                              ObjectMapper objectMapper) {
        this.idempotency  = idempotency;
        this.security     = security;
        this.routing      = routing;
        this.executor     = executor;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {

        onException(Exception.class)
                .maximumRedeliveries(3).redeliveryDelay(1000).handled(true)
                .log("KAFKA ERROR [${exchangeProperty.correlationId}]: ${exception.message}")
                .to("kafka:integration.inbound.DLT")
                .process(ex -> idempotency.markFailed(
                        ex.getProperty("correlationId", String.class)));

        // Kafka consumer -> bounded SEDA (backpressure)
        from("kafka:integration.inbound?groupId=int-framework&autoOffsetReset=earliest" +
                "&maxPollRecords=100&autoCommitEnable=false")
                .routeId("kafka-trigger")
                .to("seda:kafka-processing?size=1000&blockWhenFull=true");

        from("seda:kafka-processing?concurrentConsumers=4")
                .routeId("kafka-process")
                .process(normalise())
                .process(idempotency)
                .filter(exchangeProperty(IdempotencyFilter.PROP_SKIP).isEqualTo(false))
                  .process(security)
                  .process(routing)
                  .dynamicRouter(method(executor, "nextStep"))
                  .process(ex -> idempotency.markCompleted(
                          ex.getProperty("correlationId", String.class)));
    }

    private Processor normalise() {
        return exchange -> {
            String body = exchange.getIn().getBody(String.class);
            JsonNode payload = (body != null && !body.isBlank())
                    ? objectMapper.readTree(body) : objectMapper.createObjectNode();
            String cid = h(exchange, "X-Correlation-Id", UUID.randomUUID().toString());
            Map<String, String> headers = new HashMap<>();
            exchange.getIn().getHeaders().forEach((k, v) -> { if (v != null) headers.put(k, v.toString()); });
            exchange.getIn().setBody(IntegrationEnvelope.ofDefaults(
                    cid, "KAFKA",
                    h(exchange, "X-Source-System", "KAFKA_SOURCE"),
                    h(exchange, "X-Entity-Type",   "UNKNOWN"),
                    h(exchange, "X-Operation",     "CREATE"),
                    headers, payload));
            exchange.setProperty("correlationId", cid);
        };
    }

    private String h(Exchange ex, String name, String def) {
        Object v = ex.getIn().getHeader(name); return v != null ? v.toString() : def;
    }
}
