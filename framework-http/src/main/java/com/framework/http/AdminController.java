package com.framework.http;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RequestLogRepository requestLogRepo;
    private final XsltVersionService xsltVersionService;

    public AdminController(RequestLogRepository requestLogRepo,
                           XsltVersionService xsltVersionService) {
        this.requestLogRepo = requestLogRepo;
        this.xsltVersionService = xsltVersionService;
    }

    // ── Request log ──────────────────────────────────────────────────────────

    @GetMapping("/requests")
    public Page<RequestLog> listRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return requestLogRepo.findAllByOrderByReceivedAtDesc(PageRequest.of(page, size));
    }

    @GetMapping("/requests/stats")
    public Map<String, Object> getStats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : requestLogRepo.countByStatus()) {
            byStatus.put((String) row[0], (Long) row[1]);
        }
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        Map<String, Long> byHour = new LinkedHashMap<>();
        for (RequestLog r : requestLogRepo.findByReceivedAtAfterOrderByReceivedAtAsc(since)) {
            String hour = r.getReceivedAt().truncatedTo(ChronoUnit.HOURS).toString();
            byHour.merge(hour, 1L, Long::sum);
        }
        return Map.of(
            "total", requestLogRepo.count(),
            "byStatus", byStatus,
            "recentByHour", byHour
        );
    }

    @GetMapping("/requests/{correlationId}")
    public ResponseEntity<RequestLog> getRequest(@PathVariable String correlationId) {
        return requestLogRepo.findByCorrelationId(correlationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── XSLT versions ────────────────────────────────────────────────────────

    @GetMapping("/xslt")
    public List<XsltVersion> listXslt() {
        return xsltVersionService.listAll();
    }

    @GetMapping("/xslt/{filename}")
    public List<XsltVersion> listXsltVersions(@PathVariable String filename) {
        return xsltVersionService.listByFilename(filename);
    }

    @PostMapping("/xslt/{filename}")
    public ResponseEntity<?> uploadXslt(
            @PathVariable String filename,
            @RequestBody String content,
            @RequestParam(required = false) String comment) {
        try {
            XsltVersion saved = xsltVersionService.upload(filename, content,
                    comment != null ? comment : "Uploaded via API");
            return ResponseEntity.ok(Map.of(
                    "filename", saved.getFilename(),
                    "version", saved.getVersion(),
                    "active", saved.isActive()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/xslt/{filename}/activate/{version}")
    public ResponseEntity<?> activateXslt(
            @PathVariable String filename,
            @PathVariable int version) {
        try {
            XsltVersion activated = xsltVersionService.activate(filename, version);
            return ResponseEntity.ok(Map.of(
                    "filename", activated.getFilename(),
                    "version", activated.getVersion(),
                    "active", activated.isActive()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
