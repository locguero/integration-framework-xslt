package com.framework.cron;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CronRequestTypeRepository extends JpaRepository<CronRequestType, Long> {
    List<CronRequestType> findByActiveTrueOrderByNameAsc();
    List<CronRequestType> findAllByOrderByNameAsc();
}
