package com.framework.http;

import com.framework.cron.CronRequestType;
import com.framework.cron.CronRequestTypeRepository;
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

    private final RequestLogRepository      requestLogRepo;
    private final XsltVersionService        xsltVersionService;
    private final CronRequestTypeRepository cronRequestTypeRepo;

    public AdminController(RequestLogRepository requestLogRepo,
                           XsltVersionService xsltVersionService,
                           CronRequestTypeRepository cronRequestTypeRepo) {
        this.requestLogRepo      = requestLogRepo;
        this.xsltVersionService  = xsltVersionService;
        this.cronRequestTypeRepo = cronRequestTypeRepo;
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

    // ── Cron request-type configuration ──────────────────────────────────────

    @GetMapping("/cron-types")
    public List<CronRequestType> listCronTypes() {
        return cronRequestTypeRepo.findAllByOrderByNameAsc();
    }

    @GetMapping("/cron-types/{id}")
    public ResponseEntity<CronRequestType> getCronType(@PathVariable Long id) {
        return cronRequestTypeRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/cron-types")
    public ResponseEntity<?> createCronType(@RequestBody CronRequestType body) {
        try {
            body.setCreatedAt(Instant.now());
            body.setActive(true);
            body.setDisabledAt(null);
            body.setDisabledBy(null);
            CronRequestType saved = cronRequestTypeRepo.save(body);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/cron-types/{id}")
    public ResponseEntity<?> updateCronType(@PathVariable Long id,
                                             @RequestBody CronRequestType body) {
        return cronRequestTypeRepo.findById(id).map(existing -> {
            existing.setName(body.getName());
            existing.setSourceSystem(body.getSourceSystem());
            existing.setEntityType(body.getEntityType());
            existing.setOperation(body.getOperation());
            existing.setNotes(body.getNotes());
            return ResponseEntity.ok((Object) cronRequestTypeRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Enable or disable a request type.
     * Pass {@code ?by=username} to record who made the change.
     */
    @PutMapping("/cron-types/{id}/toggle")
    public ResponseEntity<?> toggleCronType(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "admin") String by) {
        return cronRequestTypeRepo.findById(id).map(existing -> {
            boolean nowActive = !existing.isActive();
            existing.setActive(nowActive);
            if (nowActive) {
                existing.setDisabledAt(null);
                existing.setDisabledBy(null);
            } else {
                existing.setDisabledAt(Instant.now());
                existing.setDisabledBy(by);
            }
            return ResponseEntity.ok((Object) cronRequestTypeRepo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/cron-types/{id}")
    public ResponseEntity<Void> deleteCronType(@PathVariable Long id) {
        if (!cronRequestTypeRepo.existsById(id)) return ResponseEntity.notFound().build();
        cronRequestTypeRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
