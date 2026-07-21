package com.crime.detection.backend_service.repository;

import com.crime.detection.backend_service.model.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {
    List<Rule> findByActiveTrue();
}
