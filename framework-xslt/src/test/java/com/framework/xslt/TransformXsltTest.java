package com.framework.xslt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.framework.core.model.IntegrationEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for XSLT transformation stylesheets.
 * Validates field mapping, conditional logic, and default values.
 */
class TransformXsltTest {

    private XsltTransformEngine engine;
    private EnvelopeXmlConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        converter = new EnvelopeXmlConverter(new ObjectMapper());
        engine    = new XsltTransformEngine();
    }

    @Test
    void crmUserCreate_shouldMapToIamFormat() throws Exception {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("id",        "u-123");
        payload.put("firstName", "Jane");
        payload.put("lastName",  "Doe");
        payload.put("email",     "JANE@EXAMPLE.COM");
        payload.put("role",      "admin");
        payload.put("status",    "active");

        IntegrationEnvelope env = IntegrationEnvelope.ofDefaults(
                "corr-1", "HTTP", "CRM", "USER", "CREATE", Map.of(), payload);
        String result = engine.transform("transform-to-iam", converter.toXml(env));

        assertThat(result).contains("<givenName>Jane</givenName>");
        assertThat(result).contains("<familyName>Doe</familyName>");
        assertThat(result).contains("<emailAddress>jane@example.com</emailAddress>"); // lowercased
        assertThat(result).contains("<iamRole>ADMIN</iamRole>");                      // uppercased
        assertThat(result).contains("<accountStatus>ENABLED</accountStatus>");
        assertThat(result).contains("<actionType>PROVISION</actionType>");             // CREATE->PROVISION
    }

    @Test
    void crmUserModify_shouldMapActionTypeToUpdate() throws Exception {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("id", "u-456");
        payload.put("firstName", "Bob");
        payload.put("lastName",  "Smith");
        payload.put("email",     "bob@example.com");

        IntegrationEnvelope env = IntegrationEnvelope.ofDefaults(
                "corr-2", "HTTP", "CRM", "USER", "MODIFY", Map.of(), payload);
        String result = engine.transform("transform-to-iam", converter.toXml(env));

        assertThat(result).contains("<actionType>UPDATE</actionType>");
        assertThat(result).contains("<iamRole>STANDARD_USER</iamRole>"); // default role
    }

    @Test
    void erpOrder_shouldFlattenShippingAddress() throws Exception {
        ObjectMapper om = new ObjectMapper();
        ObjectNode payload = om.createObjectNode();
        payload.put("id",      "o-789");
        payload.put("orderId", "ORD-789");
        payload.put("status",  "PENDING");
        payload.put("amount",  250.5);
        ObjectNode addr = om.createObjectNode();
        addr.put("street",     "123 Main St");
        addr.put("city",       "Springfield");
        addr.put("postalCode", "62701");
        addr.put("country",    "us");
        payload.set("shippingAddress", addr);

        IntegrationEnvelope env = IntegrationEnvelope.ofDefaults(
                "corr-3", "KAFKA", "ERP", "ORDER", "CREATE", Map.of(), payload);
        String result = engine.transform("transform-to-wms", converter.toXml(env));

        assertThat(result).contains("<line1>123 Main St</line1>");
        assertThat(result).contains("<city>Springfield</city>");
        assertThat(result).contains("<country>US</country>"); // uppercased
        assertThat(result).contains("<amount>250.50</amount>"); // formatted
    }
}
