package com.framework.xslt;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Named step routes for XSLT-based validation and transformation.
 * These are called by the RoutingSlipExecutor dynamicRouter.
 *
 * Validation step: invokes XsltRoutingEngine validation stylesheet.
 * Transform steps: invoke XsltTransformEngine with the appropriate stylesheet.
 *
 * Note: In the XSLT edition, routing AND transformation use the same
 * Saxon processor. Validation can be inlined in routing-decision.xsl
 * or separated into validation-rules.xsl for complex cases.
 */
@Component
public class XsltStepRoutes extends RouteBuilder {

    private final XsltTransformProcessor transformProcessor;

    public XsltStepRoutes(XsltTransformProcessor transformProcessor) {
        this.transformProcessor = transformProcessor;
    }

    @Override
    public void configure() {

        // ── Validation steps (XSLT-driven) ─────────────────────────────
        // Validation logic is embedded in routing-decision.xsl for simple cases.
        // For complex cross-field validation, wire to validation-rules.xsl here.
        from("direct:step-validate-user")
                .routeId("step-validate-user")
                .log("Validation step: USER correlationId=${exchangeProperty.correlationId}")
                // Validation already applied in routing-decision.xsl; this is a no-op
                // hook for additional runtime checks (e.g. DB lookups, rate limits).
                .log("Validation passed");

        from("direct:step-validate-order")
                .routeId("step-validate-order")
                .log("Validation step: ORDER correlationId=${exchangeProperty.correlationId}");

        // ── Transformation steps ────────────────────────────────────────
        from("direct:step-transform-iam")
                .routeId("step-transform-iam")
                .log("XSLT transform -> IAM: correlationId=${exchangeProperty.correlationId}")
                .setProperty("CURRENT_TRANSFORM_STEP", constant("transform-to-iam"))
                .process(transformProcessor)
                .log("IAM transform complete");

        from("direct:step-transform-wms")
                .routeId("step-transform-wms")
                .log("XSLT transform -> WMS: correlationId=${exchangeProperty.correlationId}")
                .setProperty("CURRENT_TRANSFORM_STEP", constant("transform-to-wms"))
                .process(transformProcessor)
                .log("WMS transform complete");

        from("direct:step-transform-generic")
                .routeId("step-transform-generic")
                .log("XSLT transform -> Generic: correlationId=${exchangeProperty.correlationId}")
                .setProperty("CURRENT_TRANSFORM_STEP", constant("transform-generic"))
                .process(transformProcessor)
                .log("Generic transform complete");
    }
}
