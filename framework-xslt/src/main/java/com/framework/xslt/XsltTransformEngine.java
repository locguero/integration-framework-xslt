package com.framework.xslt;

import net.sf.saxon.s9api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XSLT Transform Engine.
 *
 * Replaces AtlasMap + JOLT as the structural field-mapping layer.
 * Each destination system has its own .xsl stylesheet that maps
 * the canonical envelope payload XML to the target contract format.
 *
 * Stylesheets are pre-compiled at startup and cached for performance.
 * Saxon HE XSLT 2.0 supports: xsl:choose, xsl:if, regex, grouping,
 * XPath functions, format-date(), string manipulation, and more.
 *
 * Available stylesheets (under xslt/transform/):
 *   crm-user-to-iam.xsl     CRM User payload  -> IAM REST API body
 *   erp-order-to-wms.xsl    ERP Order payload -> WMS Kafka event
 *   generic-batch.xsl       Generic payload   -> Batch DB format
 *
 * Trade-off vs AtlasMap:
 *   + Handles complex conditional mapping natively (xsl:choose)
 *   + No drag-and-drop UI dependency to deploy/maintain
 *   + Engineers can test with any XSLT processor or IDE plugin
 *   - No BA-accessible GUI; all changes require engineer + Git PR
 */
@Component
public class XsltTransformEngine {

    private static final Logger log = LoggerFactory.getLogger(XsltTransformEngine.class);

    private final Processor saxonProcessor;
    private final Map<String, XsltExecutable> cache = new ConcurrentHashMap<>();

    // Stylesheet registry: step-name -> classpath resource
    private static final Map<String, String> STYLESHEETS = Map.of(
            "transform-to-iam",    "xslt/transform/crm-user-to-iam.xsl",
            "transform-to-wms",    "xslt/transform/erp-order-to-wms.xsl",
            "transform-generic",   "xslt/transform/generic-batch.xsl"
    );

    public XsltTransformEngine() throws Exception {
        this.saxonProcessor = new Processor(false);
        XsltCompiler compiler = saxonProcessor.newXsltCompiler();
        for (Map.Entry<String, String> e : STYLESHEETS.entrySet()) {
            try (var is = new ClassPathResource(e.getValue()).getInputStream()) {
                cache.put(e.getKey(), compiler.compile(new StreamSource(is)));
                log.info("Compiled transform stylesheet: {} -> {}", e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Apply the stylesheet registered for the given step name to the input XML.
     *
     * @param stepName  logical step name (e.g. "transform-to-iam")
     * @param inputXml  envelope or payload XML string
     * @return          transformed XML string ready for delivery
     */
    public String transform(String stepName, String inputXml) throws Exception {
        XsltExecutable executable = cache.get(stepName);
        if (executable == null) throw new IllegalArgumentException("No stylesheet for step: " + stepName);

        XsltTransformer transformer = executable.load();
        transformer.setInitialContextNode(
                saxonProcessor.newDocumentBuilder()
                        .build(new StreamSource(new StringReader(inputXml))));

        StringWriter writer = new StringWriter();
        Serializer serializer = saxonProcessor.newSerializer(writer);
        serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
        transformer.setDestination(serializer);
        transformer.transform();
        String result = writer.toString();
        log.debug("Transform [{}] complete: {} chars", stepName, result.length());
        return result;
    }
}
