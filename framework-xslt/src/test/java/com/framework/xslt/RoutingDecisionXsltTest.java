package com.framework.xslt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.core.model.IntegrationEnvelope;
import com.framework.core.model.RoutingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the XSLT routing decision stylesheet.
 * These tests run in milliseconds – no Spring context needed.
 *
 * Each test corresponds to a rule in routing-decision.xsl.
 * When a new routing rule is added to the stylesheet, add a test here.
 */
class RoutingDecisionXsltTest {

    private XsltRoutingEngine engine;
    private EnvelopeXmlConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        converter = new EnvelopeXmlConverter(new ObjectMapper());
        engine    = new XsltRoutingEngine(converter);
    }

    @Test
    void crmUserCreate_shouldRouteToIam() throws Exception {
        RoutingResult result = engine.evaluate(envelope("CRM", "USER", "CREATE", "HTTP"));
        assertThat(result.routingSlip()).containsExactly(
                "enrich-user", "validate-user", "transform-to-iam", "deliver-rest");
        assertThat(result.executionMode()).isEqualTo("TRANSIENT");
        assertThat(result.destination()).isEqualTo("IAM_REST_API");
        assertThat(result.slaClass()).isEqualTo("PRIORITY");
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    void crmUserDelete_shouldBeRejected() throws Exception {
        RoutingResult result = engine.evaluate(envelope("CRM", "USER", "DELETE", "HTTP"));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.rejectionReason()).contains("DELETE");
        assertThat(result.routingSlip()).containsExactly("dead-letter");
    }

    @Test
    void erpOrderUpdate_shouldRouteToWms_transient() throws Exception {
        RoutingResult result = engine.evaluate(envelope("ERP", "ORDER", "UPDATE", "KAFKA"));
        assertThat(result.routingSlip()).containsExactly(
                "validate-order", "transform-to-wms", "deliver-kafka");
        assertThat(result.executionMode()).isEqualTo("TRANSIENT");
    }

    @Test
    void erpOrderCreate_shouldBeMarkedDurable() throws Exception {
        RoutingResult result = engine.evaluate(envelope("ERP", "ORDER", "CREATE", "KAFKA"));
        assertThat(result.isDurable()).isTrue();
    }

    @Test
    void cronTrigger_shouldRouteToBatch() throws Exception {
        RoutingResult result = engine.evaluate(envelope("ERP", "ORDER", "CREATE", "CRON"));
        assertThat(result.slaClass()).isEqualTo("BATCH");
        assertThat(result.routingSlip()).contains("deliver-db");
    }

    @Test
    void unknownSource_shouldRouteToDeadLetter() throws Exception {
        RoutingResult result = engine.evaluate(envelope("UNKNOWN", "THING", "DO", "HTTP"));
        assertThat(result.routingSlip()).containsExactly("dead-letter");
        assertThat(result.isApproved()).isFalse();
    }

    private IntegrationEnvelope envelope(String src, String ent, String op, String trigger) {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("id", "test-id-123");
        return IntegrationEnvelope.ofDefaults(
                "corr-test", trigger, src, ent, op, Map.of(), payload);
    }
}
