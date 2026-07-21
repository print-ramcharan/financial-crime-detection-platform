package com.crime.detection.backend_service.repository;

import com.crime.detection.backend_service.model.Explanation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExplanationRepository extends JpaRepository<Explanation, String> {
    List<Explanation> findByTransactionId(String transactionId);
}
