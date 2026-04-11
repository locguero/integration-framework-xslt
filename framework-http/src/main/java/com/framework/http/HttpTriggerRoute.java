package com.framework.http;

import com.framework.core.idempotency.IdempotencyFilter;
import com.framework.core.model.IntegrationEnvelope;
import com.framework.core.model.RoutingResult;
import com.framework.core.routing.RoutingSlipExecutor;
import com.framework.core.security.SecurityContextExtractor;
import com.framework.xslt.XsltRoutingProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
public class HttpTriggerRoute extends RouteBuilder {

    private final HttpEnvelopeNormalizer   normalizer;
    private final IdempotencyFilter        idempotency;
    private final SecurityContextExtractor security;
    private final XsltRoutingProcessor     routing;
    private final RoutingSlipExecutor      executor;
    private final EnrichmentStep           enrichment;
    private final RequestLogService        requestLog;

    public HttpTriggerRoute(HttpEnvelopeNormalizer normalizer,
                            IdempotencyFilter idempotency,
                            SecurityContextExtractor security,
                            XsltRoutingProcessor routing,
                            RoutingSlipExecutor executor,
                            EnrichmentStep enrichment,
                            RequestLogService requestLog) {
        this.normalizer  = normalizer;
        this.idempotency = idempotency;
        this.security    = security;
        this.routing     = routing;
        this.executor    = executor;
        this.enrichment  = enrichment;
        this.requestLog  = requestLog;
    }

    @Override
    public void configure() {

        onException(Exception.class)
                .handled(true)
                .log("HTTP ERROR [${exchangeProperty.correlationId}]: ${exception.message}")
                .process(ex -> {
                    String cid = ex.getProperty("correlationId", String.class);
                    Exception e = ex.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (cid != null) requestLog.failed(cid, e != null ? e.getMessage() : "Unknown error");
                })
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setBody(simple("{\"error\":\"Internal error\",\"correlationId\":\"${exchangeProperty.correlationId}\"}"))
                .setHeader("Content-Type", constant("application/json"));

        from("servlet:/integration/ingest?httpMethodRestrict=POST")
                .routeId("http-trigger")
                .process(normalizer)
                .process(ex -> requestLog.received(ex.getIn().getBody(IntegrationEnvelope.class)))
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
                .process(routing)
                .choice()
                  .when(method(this, "isRejected"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(422))
                    .process(ex -> {
                        RoutingResult rr = ex.getProperty("ROUTING_RESULT", RoutingResult.class);
                        String cid = ex.getProperty("correlationId", String.class);
                        requestLog.rejected(cid, rr.rejectionReason());
                        ex.getIn().setBody("{\"status\":\"REJECTED\",\"reason\":\""
                                + rr.rejectionReason() + "\"}");
                    })
                  .otherwise()
                    .dynamicRouter(method(executor, "nextStep"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
                    .process(ex -> {
                        String cid = ex.getProperty("correlationId", String.class);
                        RoutingResult rr = ex.getProperty("ROUTING_RESULT", RoutingResult.class);
                        requestLog.accepted(cid, rr != null ? String.join(",", rr.routingSlip()) : null);
                        idempotency.markCompleted(cid);
                        ex.getIn().setBody("{\"status\":\"ACCEPTED\",\"correlationId\":\"" + cid + "\"}");
                    })
                .end();

        from("direct:step-enrich-user")
                .routeId("step-enrich-user")
                .process(enrichment);
    }

    public boolean isRejected(Exchange ex) {
        RoutingResult rr = ex.getProperty("ROUTING_RESULT", RoutingResult.class);
        return rr != null && !rr.isApproved();
    }
}
