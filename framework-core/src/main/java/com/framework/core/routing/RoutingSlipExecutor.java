package com.framework.core.routing;

import com.framework.core.model.RoutingResult;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Dynamic router called by Camel's .dynamicRouter() DSL.
 * Consumes the RoutingResult stored in the exchange properties step-by-step.
 * Returns null when the routing slip is exhausted.
 */
@Component
public class RoutingSlipExecutor {

    private static final Logger log = LoggerFactory.getLogger(RoutingSlipExecutor.class);
    private static final String INDEX_PROP = "SLIP_INDEX";

    private final StepRegistry registry;
    public RoutingSlipExecutor(StepRegistry registry) { this.registry = registry; }

    public String nextStep(Exchange exchange) {
        RoutingResult rr = exchange.getProperty("ROUTING_RESULT", RoutingResult.class);
        if (rr == null || rr.routingSlip() == null || rr.routingSlip().isEmpty()) return null;

        List<String> slip = rr.routingSlip();
        int index = exchange.getProperty(INDEX_PROP, 0, Integer.class);
        if (index >= slip.size()) { exchange.removeProperty(INDEX_PROP); return null; }

        String step = slip.get(index);
        String uri  = registry.resolveUri(step);
        log.info("Routing slip [{}/{}] step={} uri={}", index + 1, slip.size(), step, uri);
        exchange.setProperty(INDEX_PROP, index + 1);
        return uri;
    }
}
