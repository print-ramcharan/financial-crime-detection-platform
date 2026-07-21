package com.crime.detection.backend_service.repository;

import com.crime.detection.backend_service.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {
    List<Alert> findByStatus(String status);
}
