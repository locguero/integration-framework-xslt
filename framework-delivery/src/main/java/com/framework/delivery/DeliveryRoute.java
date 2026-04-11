package com.framework.delivery;

import com.framework.core.security.SecurityContextExtractor;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Delivery Step Routes.
 * Receives transformed XML from the XSLT step and delivers to destination.
 * JWT forwarded to all REST destinations; Kafka headers carry trace context.
 */
@Component
public class DeliveryRoute extends RouteBuilder {

    @Value("${framework.delivery.iam-api.base-url:http://iam-service:8082}")
    private String iamBaseUrl;

    @Value("${framework.delivery.wms.kafka-topic:wms.orders.inbound}")
    private String wmsTopic;

    @Override
    public void configure() {

        from("direct:step-deliver-rest")
                .routeId("step-deliver-rest")
                .log("REST delivery: correlationId=${exchangeProperty.correlationId}")
                .process(exchange -> {
                    String jwt = exchange.getProperty(SecurityContextExtractor.PROP_JWT_RAW, String.class);
                    if (jwt != null) exchange.getIn().setHeader("Authorization", "Bearer " + jwt);
                    exchange.getIn().setHeader("X-Correlation-Id",
                            exchange.getProperty("correlationId", String.class));
                })
                .toD(iamBaseUrl + "/api/users?bridgeEndpoint=true")
                .log("REST delivery complete: status=${header.CamelHttpResponseCode}");

        from("direct:step-deliver-kafka")
                .routeId("step-deliver-kafka")
                .log("Kafka delivery: correlationId=${exchangeProperty.correlationId}")
                .process(ex -> ex.getIn().setHeader("kafka.KEY",
                        ex.getProperty("correlationId", String.class)))
                .toD("kafka:" + wmsTopic)
                .log("Kafka delivery complete");

        from("direct:step-deliver-db")
                .routeId("step-deliver-db")
                .log("DB delivery: correlationId=${exchangeProperty.correlationId}")
                .to("jpa:com.framework.delivery.BatchRecord")
                .log("DB delivery complete");

        from("direct:step-deliver-async-hold")
                .routeId("step-deliver-async-hold")
                .log("ASYNC HOLD: correlationId=${exchangeProperty.correlationId}")
                .to("kafka:integration.hold.queue");

        from("direct:step-deliver-retry-queue")
                .routeId("step-deliver-retry-queue")
                .log("RETRY QUEUE: correlationId=${exchangeProperty.correlationId}")
                .to("kafka:integration.retry.queue");

        from("direct:step-alert-ops")
                .routeId("step-alert-ops")
                .log("OPS ALERT: correlationId=${exchangeProperty.correlationId}");
                // TODO: wire to PagerDuty / Slack

        from("direct:step-dead-letter")
                .routeId("step-dead-letter")
                .log("DEAD LETTER: correlationId=${exchangeProperty.correlationId}")
                .to("kafka:integration.dead-letter");
    }
}
