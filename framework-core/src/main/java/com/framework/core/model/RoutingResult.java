package com.framework.core.model;

import java.util.List;

/**
 * Output from the XSLT Routing Engine.
 * Replaces the former DMN DecisionResult.
 *
 * The XSLT stylesheet routing/routing-decision.xsl is evaluated against the
 * envelope XML and produces a <routingResult> document from which this record
 * is parsed. Zero side-effects – the stylesheet reads only, never writes.
 *
 * executionMode: TRANSIENT (Camel dynamicRouter) | DURABLE (Zeebe hand-off)
 * destination:   logical destination key resolved by DeliveryRoute
 * slaClass:      PRIORITY | STANDARD | BATCH
 */
public record RoutingResult(
        List<String> routingSlip,
        String       executionMode,
        String       destination,
        String       slaClass,
        String       validationResult,
        String       rejectionReason
) {
    public boolean isApproved() { return !"REJECTED".equalsIgnoreCase(validationResult); }
    public boolean isDurable()  { return "DURABLE".equalsIgnoreCase(executionMode); }
}
