package com.framework.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Local dev stub for the IAM service.
 * Accepts CRM USER payloads and returns 201 Created so the full
 * routing slip can complete without a real downstream service.
 *
 * In production, set IAM_BASE_URL env var to the real IAM service.
 */
@RestController
@RequestMapping("/mock/iam")
public class MockIamController {

    private static final Logger log = LoggerFactory.getLogger(MockIamController.class);

    @PostMapping("/api/users")
    public ResponseEntity<String> upsertUser(
            @RequestBody(required = false) String body,
            @RequestHeader Map<String, String> headers) {

        String correlationId = headers.getOrDefault("x-correlation-id", "unknown");
        log.info("[MOCK IAM] POST /api/users correlationId={} body={}", correlationId, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("{\"status\":\"created\",\"correlationId\":\"" + correlationId + "\"}");
    }
}
