package com.crime.detection.backend_service.repository;

import com.crime.detection.backend_service.model.SarReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SarReportRepository extends JpaRepository<SarReport, String> {
    Optional<SarReport> findByAlertId(String alertId);
}
