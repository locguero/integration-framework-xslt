package com.framework.xslt;

import com.framework.core.model.IntegrationEnvelope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Camel Processor adapter for the XSLT transformation step.
 *
 * Converts the envelope to XML, applies the appropriate stylesheet
 * (resolved from the ROUTING_RESULT destination), and sets the
 * transformed XML as the new message body for the delivery step.
 *
 * Usage in route:
 *   from("direct:step-transform-iam")
 *     .process(xsltTransformProcessor);
 */
@Component
public class XsltTransformProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(XsltTransformProcessor.class);

    private final XsltTransformEngine engine;
    private final EnvelopeXmlConverter converter;

    public XsltTransformProcessor(XsltTransformEngine engine, EnvelopeXmlConverter converter) {
        this.engine    = engine;
        this.converter = converter;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // Determine which stylesheet to use from the route ID
        String routeId   = exchange.getFromRouteId();                         // e.g. step-transform-iam
        String stepName  = routeId.replace("step-", "transform-");           // e.g. transform-to-iam... adjust as needed
        // Resolve step name stored when route was entered
        String resolvedStep = exchange.getProperty("CURRENT_TRANSFORM_STEP", stepName, String.class);

        IntegrationEnvelope env = exchange.getIn().getBody(IntegrationEnvelope.class);
        String envelopeXml = converter.toXml(env);
        String transformed = engine.transform(resolvedStep, envelopeXml);

        log.info("XSLT transform [{}] complete for correlationId={}",
                resolvedStep, env.correlationId());

        // Replace body with transformed XML; delivery step will forward it
        exchange.getIn().setBody(transformed);
        exchange.getIn().setHeader("Content-Type", "application/xml");
    }
}
