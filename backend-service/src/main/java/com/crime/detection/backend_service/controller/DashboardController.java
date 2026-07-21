package com.crime.detection.backend_service.controller;

import com.crime.detection.backend_service.model.*;
import com.crime.detection.backend_service.repository.*;
import com.crime.detection.backend_service.service.GraphAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SarReportRepository sarReportRepository;

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private ExplanationRepository explanationRepository;

    @Autowired
    private GraphAnalysisService graphAnalysisService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        long totalTransactions = transactionRepository.count();
        long openAlerts = alertRepository.findByStatus("OPEN").size();
        long totalAlerts = alertRepository.count();
        long totalSars = sarReportRepository.count();
        
        double avgRiskScore = riskScoreRepository.findAll().stream()
                .mapToDouble(RiskScore::getScore)
                .average()
                .orElse(0.0);

        stats.put("totalTransactions", totalTransactions);
        stats.put("openAlerts", openAlerts);
        stats.put("totalAlerts", totalAlerts);
        stats.put("totalSars", totalSars);
        stats.put("averageRiskScore", avgRiskScore);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<Alert>> getAlerts() {
        return ResponseEntity.ok(alertRepository.findAll());
    }

    @GetMapping("/alerts/{alertId}/sar")
    public ResponseEntity<SarReport> getSarReport(@PathVariable String alertId) {
        return sarReportRepository.findByAlertId(alertId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<Alert> resolveAlert(@PathVariable String alertId, @RequestParam String status) {
        return alertRepository.findById(alertId)
                .map(alert -> {
                    alert.setStatus(status);
                    return ResponseEntity.ok(alertRepository.save(alert));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/graph")
    public ResponseEntity<GraphAnalysisService.GraphData> getGraphData() {
        return ResponseEntity.ok(graphAnalysisService.analyzeNetworks());
    }

    @GetMapping("/transaction/{txId}/details")
    public ResponseEntity<Map<String, Object>> getTxDetails(@PathVariable String txId) {
        Map<String, Object> details = new HashMap<>();
        Transaction tx = transactionRepository.findById(txId).orElse(null);
        RiskScore score = riskScoreRepository.findByTransactionId(txId).orElse(null);
        List<Explanation> explanations = explanationRepository.findByTransactionId(txId);

        details.put("transaction", tx);
        details.put("riskScore", score);
        details.put("explanations", explanations);

        return ResponseEntity.ok(details);
    }
}
