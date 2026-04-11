package com.framework.http;

import com.framework.core.idempotency.IdempotencyFilter;
import com.framework.core.model.RoutingResult;
import com.framework.core.routing.RoutingSlipExecutor;
import com.framework.core.security.SecurityContextExtractor;
import com.framework.xslt.XsltRoutingProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * HTTP Trigger Route
 *
 * POST /integration/ingest
 *  -> normalise -> idempotency -> security
 *  -> XSLT routing decision (routing-decision.xsl)
 *  -> dynamic routing slip execution
 *  -> HTTP 202 ACCEPTED
 */
@Component
public class HttpTriggerRoute extends RouteBuilder {

    private final HttpEnvelopeNormalizer   normalizer;
    private final IdempotencyFilter        idempotency;
    private final SecurityContextExtractor security;
    private final XsltRoutingProcessor     routing;
    private final RoutingSlipExecutor      executor;
    private final EnrichmentStep           enrichment;

    public HttpTriggerRoute(HttpEnvelopeNormalizer normalizer,
                            IdempotencyFilter idempotency,
                            SecurityContextExtractor security,
                            XsltRoutingProcessor routing,
                            RoutingSlipExecutor executor,
                            EnrichmentStep enrichment) {
        this.normalizer  = normalizer;
        this.idempotency = idempotency;
        this.security    = security;
        this.routing     = routing;
        this.executor    = executor;
        this.enrichment  = enrichment;
    }

    @Override
    public void configure() {

        onException(Exception.class)
                .handled(true)
                .log("HTTP ERROR [${exchangeProperty.correlationId}]: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("{\"error\":\"Internal error\",\"correlationId\":\"${exchangeProperty.correlationId}\"}"))
                .setHeader("Content-Type", constant("application/json"));

        from("servlet:/integration/ingest?httpMethodRestrict=POST")
                .routeId("http-trigger")
                .process(normalizer)
                .process(idempotency)
                .choice()
                  .when(exchangeProperty(IdempotencyFilter.PROP_SKIP).isEqualTo(true))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                    .setBody(simple("{\"status\":\"ALREADY_PROCESSED\",\"correlationId\":\"${body.correlationId}\"}"))
                  .otherwise()
                    .to("direct:http-process")
                .end()
                .setHeader("Content-Type", constant("application/json"));

        from("direct:http-process")
                .routeId("http-process")
                .process(security)
                .process(routing)      // XSLT routing-decision.xsl evaluated here
                .choice()
                  .when(method(this, "isRejected"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(422))
                    .process(ex -> {
                        RoutingResult rr = ex.getProperty("ROUTING_RESULT", RoutingResult.class);
                        ex.getIn().setBody("{\"status\":\"REJECTED\",\"reason\":\""
                                + rr.rejectionReason() + "\"}");
                    })
                  .otherwise()
                    .dynamicRouter(method(executor, "nextStep"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
                    .process(ex -> {
                        String cid = ex.getProperty("correlationId", String.class);
                        ex.getIn().setBody("{\"status\":\"ACCEPTED\",\"correlationId\":\""+ cid + "\"}");
                        idempotency.markCompleted(cid);
                    })
                .end();

        // Named step: enrichment
        from("direct:step-enrich-user")
                .routeId("step-enrich-user")
                .process(enrichment);
    }

    public boolean isRejected(Exchange ex) {
        RoutingResult rr = ex.getProperty("ROUTING_RESULT", RoutingResult.class);
        return rr != null && !rr.isApproved();
    }
}
