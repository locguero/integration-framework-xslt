package com.framework.xslt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.framework.core.model.IntegrationEnvelope;
import org.springframework.stereotype.Component;

/**
 * Converts an IntegrationEnvelope to/from an XML representation
 * suitable for processing by Saxon XSLT 2.0 stylesheets.
 *
 * Envelope XML structure:
 * <pre>
 * {@code
 * <envelope>
 *   <correlationId>...</correlationId>
 *   <triggerId>HTTP</triggerId>
 *   <sourceSystem>CRM</sourceSystem>
 *   <entityType>USER</entityType>
 *   <requestedOperation>CREATE</requestedOperation>
 *   <enrichmentStatus>PENDING</enrichmentStatus>
 *   <payload>
 *     <!-- payload JSON converted to XML via Jackson XmlMapper -->
 *   </payload>
 * </envelope>
 * }
 * </pre>
 *
 * XSLT 3.0 note: Saxon-HE 12.x supports json-to-xml() natively,
 * so payload conversion can alternatively be done inside the stylesheet.
 * We do it in Java here for cleaner separation and easier unit-testing.
 */
@Component
public class EnvelopeXmlConverter {

    private final ObjectMapper jsonMapper;
    private final XmlMapper    xmlMapper;

    public EnvelopeXmlConverter(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.xmlMapper  = new XmlMapper();
    }

    /**
     * Serialise an IntegrationEnvelope to well-formed XML String.
     * The payload JsonNode is embedded as XML child elements.
     */
    public String toXml(IntegrationEnvelope env) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<envelope>\n");
        sb.append("  <correlationId>").append(escXml(env.correlationId())).append("</correlationId>\n");
        sb.append("  <triggerId>").append(escXml(env.triggerId())).append("</triggerId>\n");
        sb.append("  <sourceSystem>").append(escXml(env.sourceSystem())).append("</sourceSystem>\n");
        sb.append("  <entityType>").append(escXml(env.entityType())).append("</entityType>\n");
        sb.append("  <requestedOperation>").append(escXml(env.requestedOperation())).append("</requestedOperation>\n");
        sb.append("  <enrichmentStatus>").append(escXml(env.enrichmentStatus())).append("</enrichmentStatus>\n");
        sb.append("  <payload>\n");
        sb.append(jsonNodeToXml(env.payload(), "    "));
        sb.append("  </payload>\n");
        sb.append("</envelope>");
        return sb.toString();
    }

    /** Recursively convert a JsonNode to XML child elements. */
    private String jsonNodeToXml(JsonNode node, String indent) {
        StringBuilder sb = new StringBuilder();
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> {
                String tag = sanitiseTag(e.getKey());
                if (e.getValue().isValueNode()) {
                    sb.append(indent).append("<").append(tag).append(">")
                      .append(escXml(e.getValue().asText()))
                      .append("</").append(tag).append(">\n");
                } else {
                    sb.append(indent).append("<").append(tag).append(">\n");
                    sb.append(jsonNodeToXml(e.getValue(), indent + "  "));
                    sb.append(indent).append("</").append(tag).append(">\n");
                }
            });
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                sb.append(indent).append("<item>\n");
                sb.append(jsonNodeToXml(item, indent + "  "));
                sb.append(indent).append("</item>\n");
            }
        } else {
            sb.append(indent).append(escXml(node.asText())).append("\n");
        }
        return sb.toString();
    }

    private String escXml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&apos;");
    }

    /** XML tag names must start with letter/underscore, no spaces. */
    private String sanitiseTag(String key) {
        String safe = key.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return safe.matches("^[0-9].*") ? "_" + safe : safe;
    }
}
