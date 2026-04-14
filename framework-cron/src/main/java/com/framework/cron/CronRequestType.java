package com.framework.cron;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Configurable request-type filter for the CRON scheduler.
 * Each active row tells the poll route which (sourceSystem, entityType, operation)
 * combination to fetch from the ERP on every scheduled tick.
 */
@Entity
@Table(name = "cron_request_type",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_system", "entity_type", "operation"}))
public class CronRequestType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable label shown in the admin UI. */
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "operation", nullable = false)
    private String operation;

    /** When false the poll route skips this type entirely. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Set when active flips to false. */
    @Column(name = "disabled_at")
    private Instant disabledAt;

    /** Username / principal that disabled this type. */
    @Column(name = "disabled_by")
    private String disabledBy;

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSourceSystem() { return sourceSystem; }
    public String getEntityType() { return entityType; }
    public String getOperation() { return operation; }
    public boolean isActive() { return active; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDisabledAt() { return disabledAt; }
    public String getDisabledBy() { return disabledBy; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setName(String name) { this.name = name; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setOperation(String operation) { this.operation = operation; }
    public void setActive(boolean active) { this.active = active; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setDisabledAt(Instant disabledAt) { this.disabledAt = disabledAt; }
    public void setDisabledBy(String disabledBy) { this.disabledBy = disabledBy; }
}
