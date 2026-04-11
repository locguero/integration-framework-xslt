package com.framework.http;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    Optional<RequestLog> findByCorrelationId(String correlationId);
    Page<RequestLog> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
