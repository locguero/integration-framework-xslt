package com.framework.http;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "xslt_version",
       uniqueConstraints = @UniqueConstraint(columnNames = {"filename", "version"}))
public class XsltVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", columnDefinition = "CLOB", nullable = false)
    private String content;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "comment")
    private String comment;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getUploadedAt() { return uploadedAt; }
}
