package com.framework.http;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
    Optional<RequestLog> findByCorrelationId(String correlationId);
    Page<RequestLog> findAllByOrderByReceivedAtDesc(Pageable pageable);

    @Query("SELECT r.status, COUNT(r) FROM RequestLog r GROUP BY r.status")
    List<Object[]> countByStatus();

    List<RequestLog> findByReceivedAtAfterOrderByReceivedAtAsc(@Param("since") Instant since);
}
