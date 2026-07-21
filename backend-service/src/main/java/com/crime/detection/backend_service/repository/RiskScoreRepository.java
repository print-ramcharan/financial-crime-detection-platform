package com.crime.detection.backend_service.repository;

import com.crime.detection.backend_service.model.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, String> {
    Optional<RiskScore> findByTransactionId(String transactionId);
}
