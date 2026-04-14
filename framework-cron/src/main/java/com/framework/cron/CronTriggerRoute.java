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
import java.util.List;
import java.util.UUID;

/**
 * Scheduled Poll Trigger.
 *
 * <p>On every cron tick (default: every 5 minutes, configurable via
 * {@code framework.cron.erp-poll.cron}) the route:
 * <ol>
 *   <li>Loads all <em>active</em> {@link CronRequestType} rows from the database.</li>
 *   <li>For each type, calls the {@link ErpPollingAdapter} with the correct
 *       sourceSystem/entityType/operation context.</li>
 *   <li>Pushes individual records onto a bounded SEDA queue for concurrent
 *       processing with back-pressure protection.</li>
 *   <li>Each record is wrapped in an {@link IntegrationEnvelope}, logged to
 *       {@code request_log}, checked for idempotency, routed via XSLT, and
 *       the watermark is updated atomically on completion.</li>
 * </ol>
 */
@Component
public class CronTriggerRoute extends RouteBuilder {

    private final IdempotencyFilter         idempotency;
    private final XsltRoutingProcessor      routing;
    private final RoutingSlipExecutor       executor;
    private final ErpPollingAdapter         erpAdapter;
    private final WatermarkRepository       watermarks;
    private final CronRequestTypeRepository requestTypes;
    private final CronRequestLogger         requestLogger;
    private final ObjectMapper              objectMapper;

    public CronTriggerRoute(IdempotencyFilter idempotency,
                             XsltRoutingProcessor routing,
                             RoutingSlipExecutor executor,
                             ErpPollingAdapter erpAdapter,
                             WatermarkRepository watermarks,
                             CronRequestTypeRepository requestTypes,
                             CronRequestLogger requestLogger,
                             ObjectMapper objectMapper) {
        this.idempotency   = idempotency;
        this.routing       = routing;
        this.executor      = executor;
        this.erpAdapter    = erpAdapter;
        this.watermarks    = watermarks;
        this.requestTypes  = requestTypes;
        this.requestLogger = requestLogger;
        this.objectMapper  = objectMapper;
    }

    @Override
    public void configure() {

        onException(Exception.class)
                .maximumRedeliveries(2).handled(true)
                .log("CRON ERROR [${exchangeProperty.correlationId}]: ${exception.message}")
                .process(ex -> {
                    String cid = ex.getProperty("correlationId", String.class);
                    Exception e = ex.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (cid != null) {
                        requestLogger.failed(cid, e != null ? e.getMessage() : "Unknown error");
                    }
                });

        // ── Scheduler: fires on the configured cron expression ────────────────
        from("quartz:erp-poll?cron={{framework.cron.erp-poll.cron:0+*/5+*+*+*+?}}")
                .routeId("cron-trigger")
                .process(ex -> {
                    List<CronRequestType> active = requestTypes.findByActiveTrueOrderByNameAsc();
                    log.info("CRON tick – {} active request type(s)", active.size());
                    ex.getIn().setBody(active);
                })
                // Outer split: one exchange per active request type
                .split(body()).streaming()
                  .process(ex -> {
                      CronRequestType type = ex.getIn().getBody(CronRequestType.class);
                      String wmKey = type.getSourceSystem() + "_" + type.getEntityType()
                                     + "_" + type.getOperation();
                      ex.setProperty("WATERMARK",    watermarks.getWatermark(wmKey));
                      ex.setProperty("REQUEST_TYPE", type);
                      ex.setProperty("WM_KEY",       wmKey);
                  })
                  .bean(erpAdapter, "fetchPendingRecords")
                  // Inner split: one exchange per record returned by the adapter
                  .split(body()).streaming()
                    .to("seda:cron-processing?size=500&blockWhenFull=false&offerTimeout=0")
                  .end()
                .end();

        // ── Processor: concurrent record handling ─────────────────────────────
        from("seda:cron-processing?concurrentConsumers=2")
                .routeId("cron-process")
                .process(ex -> {
                    Object record = ex.getIn().getBody();
                    CronRequestType type = ex.getProperty("REQUEST_TYPE", CronRequestType.class);

                    String source    = type != null ? type.getSourceSystem() : "ERP";
                    String entity    = type != null ? type.getEntityType()   : "ORDER";
                    String operation = type != null ? type.getOperation()    : "CREATE";

                    var payload = objectMapper.valueToTree(record);
                    String cid  = UUID.randomUUID().toString();

                    IntegrationEnvelope envelope = IntegrationEnvelope.ofDefaults(
                            cid, "CRON", source, entity, operation, new HashMap<>(), payload);

                    ex.getIn().setBody(envelope);
                    ex.setProperty("correlationId", cid);

                    // Write RECEIVED row — visible in admin UI immediately
                    requestLogger.received(envelope);
                })
                .process(idempotency)
                .filter(exchangeProperty(IdempotencyFilter.PROP_SKIP).isEqualTo(false))
                  .process(routing)
                  .dynamicRouter(method(executor, "nextStep"))
                  .process(ex -> {
                      String cid   = ex.getProperty("correlationId", String.class);
                      String wmKey = ex.getProperty("WM_KEY", String.class);
                      String recId = ex.getProperty("RECORD_ID", String.class);

                      // Resolve routing slip string for the log
                      var rr = ex.getProperty("ROUTING_RESULT",
                              com.framework.core.model.RoutingResult.class);
                      String slip = rr != null ? String.join(",", rr.routingSlip()) : null;

                      requestLogger.accepted(cid, slip);
                      idempotency.markCompleted(cid);

                      if (wmKey != null && recId != null) {
                          watermarks.updateWatermark(wmKey, recId);
                      }
                  });
    }
}
