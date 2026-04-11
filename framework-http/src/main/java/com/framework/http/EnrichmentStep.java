package com.framework.http;

import com.framework.core.model.IntegrationEnvelope;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

/**
 * Enrichment Step: User Existence Check.
 * FOUND     -> mutate operation CREATE->MODIFY, status->COMPLETED
 * NOT FOUND -> keep CREATE,                     status->COMPLETED
 * CB OPEN   -> status->SKIPPED  (XSLT fallback stylesheet takes over)
 */
@Component
public class EnrichmentStep implements Processor {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentStep.class);
    private final WebClient webClient;

    public EnrichmentStep(@Value("${framework.enrichment.base-url:http://user-service}") String url) {
        this.webClient = WebClient.builder().baseUrl(url).build();
    }

    @Override
    @CircuitBreaker(name = "user-lookup-api", fallbackMethod = "fallback")
    public void process(Exchange exchange) {
        IntegrationEnvelope env = exchange.getIn().getBody(IntegrationEnvelope.class);
        if (!"USER".equalsIgnoreCase(env.entityType())) return;
        String id = extractId(env);
        Boolean exists = webClient.get().uri("/users/{id}/exists", id)
                .header("Authorization", env.headers().getOrDefault("Authorization",""))
                .retrieve().bodyToMono(Boolean.class).timeout(Duration.ofSeconds(5)).block();
        exchange.getIn().setBody(Boolean.TRUE.equals(exists)
                ? env.withOperation("MODIFY").withEnrichmentStatus("COMPLETED")
                : env.withEnrichmentStatus("COMPLETED"));
    }

    public void fallback(Exchange exchange, Throwable ex) {
        log.warn("Enrichment CB OPEN – SKIPPED: {}", ex.getMessage());
        exchange.getIn().setBody(
                exchange.getIn().getBody(IntegrationEnvelope.class).withEnrichmentStatus("SKIPPED"));
    }

    private String extractId(IntegrationEnvelope env) {
        try { return env.payload().get("id").asText(); } catch (Exception e) { return "UNKNOWN"; }
    }
}
