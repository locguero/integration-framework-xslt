package com.framework.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

/**
 * Canonical inbound envelope. Every trigger normalises its input here
 * before any XSLT evaluation, enrichment, or transformation.
 */
public record IntegrationEnvelope(
        String              correlationId,
        String              triggerId,
        String              sourceSystem,
        String              entityType,
        String              requestedOperation,
        Map<String, String> headers,
        JsonNode            payload,
        Instant             receivedAt,
        String              enrichmentStatus
) {
    public static IntegrationEnvelope ofDefaults(
            String correlationId, String triggerId, String sourceSystem,
            String entityType, String requestedOperation,
            Map<String, String> headers, JsonNode payload) {
        return new IntegrationEnvelope(correlationId, triggerId, sourceSystem,
                entityType, requestedOperation, headers, payload, Instant.now(), "PENDING");
    }

    public IntegrationEnvelope withOperation(String op) {
        return new IntegrationEnvelope(correlationId, triggerId, sourceSystem,
                entityType, op, headers, payload, receivedAt, enrichmentStatus);
    }

    public IntegrationEnvelope withEnrichmentStatus(String status) {
        return new IntegrationEnvelope(correlationId, triggerId, sourceSystem,
                entityType, requestedOperation, headers, payload, receivedAt, status);
    }
}
