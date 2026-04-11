package com.framework.xslt;

import com.framework.core.model.IntegrationEnvelope;
import com.framework.core.model.RoutingResult;
import net.sf.saxon.s9api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * XSLT Routing Engine.
 *
 * Replaces the Camunda DMN Decision Gateway.
 * Uses Saxon HE (XSLT 2.0/3.0) to evaluate routing-decision.xsl
 * against the envelope XML and parse the resulting <routingResult> document.
 *
 * Stylesheet: xslt/routing/routing-decision.xsl
 * Input:  <envelope> XML (from EnvelopeXmlConverter)
 * Output: <routingResult>
 *           <routingSlip>step-a,step-b,step-c</routingSlip>
 *           <executionMode>TRANSIENT</executionMode>
 *           <destination>IAM_REST_API</destination>
 *           <slaClass>PRIORITY</slaClass>
 *           <validationResult>APPROVED</validationResult>
 *           <rejectionReason/>
 *         </routingResult>
 *
 * XSLT vs DMN trade-offs (see docs/ARCHITECTURE.md for full discussion):
 *   + Single technology for routing AND transformation
 *   + XSLT 2.0 supports full XPath 2.0 conditionals, regex, grouping, functions
 *   + W3C standard, no vendor lock-in
 *   + Stylesheets are plain text – diffable, versionable in Git
 *   + Saxon HE is free (Mozilla Public License)
 *   - No BA drag-and-drop UI (engineers must edit .xsl files)
 *   - Steeper learning curve than DMN decision tables
 */
@Component
public class XsltRoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(XsltRoutingEngine.class);

    private final Processor      saxonProcessor;
    private volatile XsltExecutable routingExecutable;
    private volatile XsltExecutable fallbackExecutable;
    private final EnvelopeXmlConverter converter;

    public XsltRoutingEngine(EnvelopeXmlConverter converter) throws Exception {
        this.converter      = converter;
        this.saxonProcessor = new Processor(false); // false = HE (not EE)
        XsltCompiler compiler = saxonProcessor.newXsltCompiler();
        this.routingExecutable  = compile(compiler, "xslt/routing/routing-decision.xsl");
        this.fallbackExecutable = compile(compiler, "xslt/routing/fallback-routing.xsl");
        log.info("XSLT routing stylesheets loaded (Saxon HE {})", net.sf.saxon.Version.getProductVersion());
    }

    public RoutingResult evaluate(IntegrationEnvelope env) throws Exception {
        log.info("XSLT routing: correlationId={} source={} entity={} op={}",
                env.correlationId(), env.sourceSystem(), env.entityType(), env.requestedOperation());
        return runStylesheet(routingExecutable, env);
    }

    public RoutingResult evaluateFallback(IntegrationEnvelope env) throws Exception {
        log.warn("XSLT fallback routing: enrichmentStatus={}", env.enrichmentStatus());
        return runStylesheet(fallbackExecutable, env);
    }

    private RoutingResult runStylesheet(XsltExecutable executable, IntegrationEnvelope env) throws Exception {
        String envXml    = converter.toXml(env);
        String resultXml = transform(executable, envXml);
        log.debug("XSLT result XML: {}", resultXml);
        return parseResult(resultXml);
    }

    private String transform(XsltExecutable executable, String inputXml) throws Exception {
        XsltTransformer transformer = executable.load();
        transformer.setInitialContextNode(
                saxonProcessor.newDocumentBuilder()
                        .build(new StreamSource(new StringReader(inputXml))));
        StringWriter writer = new StringWriter();
        Serializer serializer = saxonProcessor.newSerializer(writer);
        serializer.setOutputProperty(Serializer.Property.INDENT, "no");
        transformer.setDestination(serializer);
        transformer.transform();
        return writer.toString();
    }

    private RoutingResult parseResult(String xml) throws Exception {
        // Lightweight XML parse – avoid full DOM for performance
        String slip             = extractTag(xml, "routingSlip");
        String executionMode    = extractTag(xml, "executionMode");
        String destination      = extractTag(xml, "destination");
        String slaClass         = extractTag(xml, "slaClass");
        String validationResult = extractTag(xml, "validationResult");
        String rejectionReason  = extractTag(xml, "rejectionReason");

        List<String> steps = (slip == null || slip.isBlank())
                ? Collections.singletonList("dead-letter")
                : Arrays.stream(slip.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        return new RoutingResult(
                steps,
                coalesce(executionMode,    "TRANSIENT"),
                coalesce(destination,      "DEAD_LETTER"),
                coalesce(slaClass,         "STANDARD"),
                coalesce(validationResult, "APPROVED"),
                rejectionReason
        );
    }

    private String extractTag(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        int end   = xml.indexOf("</" + tag + ">");
        if (start < 0 || end < 0) return null;
        return xml.substring(start + tag.length() + 2, end).trim();
    }

    private String coalesce(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    public synchronized void reloadRouting(String xsltContent) throws Exception {
        this.routingExecutable = compileFromString(xsltContent);
        log.info("XSLT routing-decision reloaded from database");
    }

    public synchronized void reloadFallback(String xsltContent) throws Exception {
        this.fallbackExecutable = compileFromString(xsltContent);
        log.info("XSLT fallback-routing reloaded from database");
    }

    private XsltExecutable compile(XsltCompiler compiler, String classpathResource) throws Exception {
        try (var is = new ClassPathResource(classpathResource).getInputStream()) {
            return compiler.compile(new StreamSource(is));
        }
    }

    private XsltExecutable compileFromString(String content) throws Exception {
        return saxonProcessor.newXsltCompiler()
                .compile(new StreamSource(new StringReader(content)));
    }
}
