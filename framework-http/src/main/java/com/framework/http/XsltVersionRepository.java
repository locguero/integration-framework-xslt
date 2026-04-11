package com.framework.http;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface XsltVersionRepository extends JpaRepository<XsltVersion, Long> {
    List<XsltVersion> findByFilenameOrderByVersionDesc(String filename);
    Optional<XsltVersion> findByFilenameAndActive(String filename, boolean active);
    Optional<XsltVersion> findByFilenameAndVersion(String filename, Integer version);
    boolean existsByFilename(String filename);
    Optional<XsltVersion> findTopByFilenameOrderByVersionDesc(String filename);
}
