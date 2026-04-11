package com.framework.xslt;

import com.framework.core.model.IntegrationEnvelope;
import com.framework.core.model.RoutingResult;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

/**
 * Camel Processor adapter: invokes XsltRoutingEngine and stores
 * the RoutingResult as an exchange property for the dynamic router.
 * Delegates to fallback stylesheet when enrichmentStatus=SKIPPED.
 */
@Component
public class XsltRoutingProcessor implements Processor {

    private final XsltRoutingEngine engine;
    public XsltRoutingProcessor(XsltRoutingEngine engine) { this.engine = engine; }

    @Override
    public void process(Exchange exchange) throws Exception {
        IntegrationEnvelope env = exchange.getIn().getBody(IntegrationEnvelope.class);
        RoutingResult result = "SKIPPED".equals(env.enrichmentStatus())
                ? engine.evaluateFallback(env)
                : engine.evaluate(env);

        exchange.setProperty("ROUTING_RESULT", result);
        exchange.setProperty("correlationId",  env.correlationId());
        exchange.setProperty("sourceSystem",   env.sourceSystem());
        exchange.setProperty("entityType",     env.entityType());
        exchange.setProperty("SLA_CLASS",      result.slaClass());
    }
}
