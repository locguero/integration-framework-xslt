package com.framework.http;

import com.framework.core.model.IntegrationEnvelope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RequestLogService {

    private final RequestLogRepository repo;

    public RequestLogService(RequestLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void received(IntegrationEnvelope env) {
        RequestLog log = new RequestLog();
        log.setCorrelationId(env.correlationId());
        log.setSourceSystem(env.sourceSystem());
        log.setEntityType(env.entityType());
        log.setOperation(env.requestedOperation());
        log.setStatus("RECEIVED");
        if (env.payload() != null) {
            log.setRawPayload(env.payload().toString());
        }
        repo.save(log);
    }

    @Transactional
    public void outgoing(String correlationId, String outgoingPayload) {
        repo.findByCorrelationId(correlationId).ifPresent(log -> {
            log.setOutgoingPayload(outgoingPayload);
            repo.save(log);
        });
    }

    @Transactional
    public void accepted(String correlationId, String routingSlip) {
        repo.findByCorrelationId(correlationId).ifPresent(log -> {
            log.setStatus("ACCEPTED");
            log.setRoutingSlip(routingSlip);
            log.setCompletedAt(Instant.now());
            repo.save(log);
        });
    }

    @Transactional
    public void rejected(String correlationId, String reason) {
        repo.findByCorrelationId(correlationId).ifPresent(log -> {
            log.setStatus("REJECTED");
            log.setErrorMessage(reason);
            log.setCompletedAt(Instant.now());
            repo.save(log);
        });
    }

    @Transactional
    public void failed(String correlationId, String errorMessage) {
        repo.findByCorrelationId(correlationId).ifPresent(log -> {
            log.setStatus("FAILED");
            log.setErrorMessage(errorMessage);
            log.setCompletedAt(Instant.now());
            repo.save(log);
        });
    }
}
