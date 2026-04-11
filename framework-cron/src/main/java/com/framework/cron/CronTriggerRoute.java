package com.framework.cron;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.core.idempotency.IdempotencyFilter;
import com.framework.core.model.IntegrationEnvelope;
import com.framework.core.routing.RoutingSlipExecutor;
import com.framework.xslt.XsltRoutingProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cron / Scheduled Poll Trigger.
 * High-watermark cursor + bounded SEDA + skip-on-full for backpressure.
 */
@Component
public class CronTriggerRoute extends RouteBuilder {

    private final IdempotencyFilter    idempotency;
    private final XsltRoutingProcessor routing;
    private final RoutingSlipExecutor  executor;
    private final ErpPollingAdapter    erpAdapter;
    private final WatermarkRepository  watermarks;
    private final ObjectMapper         objectMapper;

    public CronTriggerRoute(IdempotencyFilter idempotency, XsltRoutingProcessor routing,
                             RoutingSlipExecutor executor, ErpPollingAdapter erpAdapter,
                             WatermarkRepository watermarks, ObjectMapper objectMapper) {
        this.idempotency  = idempotency;
        this.routing      = routing;
        this.executor     = executor;
        this.erpAdapter   = erpAdapter;
        this.watermarks   = watermarks;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {

        onException(Exception.class)
                .maximumRedeliveries(2).handled(true)
                .log("CRON ERROR [${exchangeProperty.correlationId}]: ${exception.message}");

        from("quartz:erp-poll?cron={{framework.cron.erp-poll.cron:0+*/5+*+*+*+?}}")
                .routeId("cron-trigger")
                .process(ex -> ex.setProperty("WATERMARK", watermarks.getWatermark("ERP_ORDERS")))
                .bean(erpAdapter, "fetchPendingRecords")
                .split(body()).streaming()
                  .to("seda:cron-processing?size=500&blockWhenFull=false&offerTimeout=0")
                .end();

        from("seda:cron-processing?concurrentConsumers=2")
                .routeId("cron-process")
                .process(ex -> {
                    Object record = ex.getIn().getBody();
                    var payload = objectMapper.valueToTree(record);
                    String cid = UUID.randomUUID().toString();
                    ex.getIn().setBody(IntegrationEnvelope.ofDefaults(
                            cid, "CRON", "ERP", "ORDER", "CREATE", new HashMap<>(), payload));
                    ex.setProperty("correlationId", cid);
                })
                .process(idempotency)
                .filter(exchangeProperty(IdempotencyFilter.PROP_SKIP).isEqualTo(false))
                  .process(routing)
                  .dynamicRouter(method(executor, "nextStep"))
                  .process(ex -> {
                      idempotency.markCompleted(ex.getProperty("correlationId", String.class));
                      String recordId = ex.getProperty("RECORD_ID", String.class);
                      if (recordId != null) watermarks.updateWatermark("ERP_ORDERS", recordId);
                  });
    }
}
