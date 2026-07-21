package com.crime.detection.backend_service.service;

import com.crime.detection.backend_service.engine.RuleEngine;
import com.crime.detection.backend_service.model.Alert;
import com.crime.detection.backend_service.model.Explanation;
import com.crime.detection.backend_service.model.RiskScore;
import com.crime.detection.backend_service.model.Rule;
import com.crime.detection.backend_service.model.Transaction;
import com.crime.detection.backend_service.repository.AlertRepository;
import com.crime.detection.backend_service.repository.ExplanationRepository;
import com.crime.detection.backend_service.repository.RiskScoreRepository;
import com.crime.detection.backend_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KafkaConsumerService {

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private ExplanationRepository explanationRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private SarGenerationService sarGenerationService;

    @Autowired
    private GraphAnalysisService graphAnalysisService;

    @KafkaListener(topics = "risk-scores", groupId = "crime-backend-group")
    public void consumeRiskScore(Map<String, Object> event) {
        try {
            String txId = (String) event.get("transaction_id");
            Double score = ((Number) event.get("risk_score")).doubleValue();
            String modelVersion = (String) event.get("model_version");
            Map<String, Object> explanationMap = (Map<String, Object>) event.get("explanation");

            // 1. Save Risk Score
            RiskScore riskScore = new RiskScore();
            riskScore.setId(UUID.randomUUID().toString());
            riskScore.setTransactionId(txId);
            riskScore.setScore(score);
            riskScore.setModelVersion(modelVersion);
            riskScore.setPredictionTime(LocalDateTime.now());
            riskScoreRepository.save(riskScore);

            // 2. Save SHAP Explanations
            if (explanationMap != null) {
                explanationMap.forEach((featureName, impactValue) -> {
                    Explanation exp = new Explanation();
                    exp.setTransactionId(txId);
                    exp.setFeatureName(featureName);
                    exp.setImpactValue(((Number) impactValue).doubleValue());
                    explanationRepository.save(exp);
                });
            }

            // 3. Retrieve original transaction for facts
            Transaction tx = transactionRepository.findById(txId).orElse(null);
            if (tx != null) {
                Map<String, Object> facts = new HashMap<>();
                facts.put("riskScore", score);
                facts.put("amount", tx.getAmount().doubleValue());
                facts.put("country", tx.getCountry());
                facts.put("eachAmount", tx.getAmount().doubleValue());
                
                // Add explanation impact values as facts if present
                if (explanationMap != null) {
                    explanationMap.forEach((k, v) -> facts.put(k, ((Number) v).doubleValue()));
                }

                // --- AML Features: Graph Analysis ---
                Map<String, Object> graphFeatures = graphAnalysisService.extractGraphFeatures(tx);
                facts.putAll(graphFeatures);

                // --- AML Features: Structuring Detection ---
                LocalDateTime last24h = LocalDateTime.now().minusHours(24);
                List<Transaction> recentSenderTxs = transactionRepository.findBySenderAccountAndTimestampAfter(tx.getSenderAccount(), last24h);
                
                int transactionsWithin24Hours = recentSenderTxs.size() + 1; // including current
                double totalAmount = tx.getAmount().doubleValue();
                for (Transaction t : recentSenderTxs) {
                    if (!t.getId().equals(tx.getId())) {
                        totalAmount += t.getAmount().doubleValue();
                    }
                }
                
                facts.put("transactionsWithin24Hours", transactionsWithin24Hours);
                facts.put("totalAmount", totalAmount);

                // Evaluate compliance rules
                Rule matchedRule = ruleEngine.evaluate(facts);
                if (matchedRule != null) {
                    // Update Transaction Status
                    tx.setStatus(matchedRule.getAction());
                    transactionRepository.save(tx);

                    // Generate Alert
                    Alert alert = new Alert();
                    alert.setId(UUID.randomUUID().toString());
                    alert.setTransactionId(txId);
                    alert.setSeverity(score > 85 ? "HIGH" : "MEDIUM");
                    alert.setReason("Triggered compliance rule: " + matchedRule.getName() + " - " + matchedRule.getDescription());
                    alert.setStatus("OPEN");
                    alert.setCreatedAt(LocalDateTime.now());
                    alertRepository.save(alert);

                    System.out.println("Alert generated for Tx: " + txId + " reason: " + alert.getReason());

                    // Generate SAR Report automatically
                    sarGenerationService.generateSarReport(alert.getId());
                } else {
                    tx.setStatus("APPROVED");
                    transactionRepository.save(tx);
                }
            }

        } catch (Exception e) {
            System.err.println("Error processing risk score event: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
