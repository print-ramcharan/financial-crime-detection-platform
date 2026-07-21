package com.crime.detection.backend_service.service;

import com.crime.detection.backend_service.model.Alert;
import com.crime.detection.backend_service.model.Explanation;
import com.crime.detection.backend_service.model.SarReport;
import com.crime.detection.backend_service.model.Transaction;
import com.crime.detection.backend_service.repository.AlertRepository;
import com.crime.detection.backend_service.repository.ExplanationRepository;
import com.crime.detection.backend_service.repository.SarReportRepository;
import com.crime.detection.backend_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SarGenerationService {

    @Autowired
    private SarReportRepository sarReportRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ExplanationRepository explanationRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    public SarReport generateSarReport(String alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        Transaction tx = transactionRepository.findById(alert.getTransactionId())
                .orElse(null);

        List<Explanation> explanations = explanationRepository.findByTransactionId(alert.getTransactionId());

        String apiKey = System.getenv("GEMINI_API_KEY");
        String reportText;

        if (apiKey == null || apiKey.isEmpty()) {
            reportText = generateMockReport(alert, tx, explanations);
        } else {
            reportText = callGeminiApi(apiKey, alert, tx, explanations);
        }

        SarReport report = new SarReport();
        report.setId(UUID.randomUUID().toString());
        report.setAlertId(alertId);
        report.setReportText(reportText);
        report.setGeneratedAt(LocalDateTime.now());
        report.setReviewStatus("PENDING");

        return sarReportRepository.save(report);
    }

    private String generateMockReport(Alert alert, Transaction tx, List<Explanation> explanations) {
        StringBuilder mock = new StringBuilder();
        mock.append("=== SUSPICIOUS ACTIVITY REPORT (SAR) ===\n");
        mock.append("Report ID: ").append(UUID.randomUUID().toString().substring(0, 8)).append("\n");
        mock.append("Alert ID: ").append(alert.getId()).append("\n");
        mock.append("Alert Reason: ").append(alert.getReason()).append("\n\n");
        if (tx != null) {
            mock.append("--- Transaction Details ---\n");
            mock.append("Tx ID: ").append(tx.getId()).append("\n");
            mock.append("Amount: ").append(tx.getAmount()).append(" ").append(tx.getCurrency()).append("\n");
            mock.append("Sender: ").append(tx.getSenderAccount()).append("\n");
            mock.append("Receiver: ").append(tx.getReceiverAccount()).append("\n");
            mock.append("Country: ").append(tx.getCountry()).append("\n\n");
        }
        mock.append("--- ML SHAP Explanation ---\n");
        for (Explanation exp : explanations) {
            mock.append("Feature: ").append(exp.getFeatureName())
                .append(", Impact: ").append(String.format("%.4f", exp.getImpactValue())).append("\n");
        }
        mock.append("\nRecommended Action: Investigate account for potential structuring/money laundering.");
        return mock.toString();
    }

    private String callGeminiApi(String apiKey, Alert alert, Transaction tx, List<Explanation> explanations) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        String featuresText = explanations.stream()
                .map(e -> e.getFeatureName() + ": " + e.getImpactValue())
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "You are an expert Anti-Money Laundering (AML) Compliance Officer. Generate a detailed, professional, regulator-ready Suspicious Activity Report (SAR) narrative based on the following alerts and features:\n\n" +
                "Alert Severity: %s\n" +
                "Alert Reason: %s\n" +
                "Transaction Amount: %s\n" +
                "Country: %s\n" +
                "Sender: %s\n" +
                "Receiver: %s\n\n" +
                "ML Explanations (SHAP values):\n%s\n\n" +
                "Include a summary, suspicious activities observed, relationship to financial crime, and recommended next steps for investigators. Make it structured, clean, and highly formal.",
                alert.getSeverity(), alert.getReason(),
                tx != null ? tx.getAmount() + " " + tx.getCurrency() : "N/A",
                tx != null ? tx.getCountry() : "N/A",
                tx != null ? tx.getSenderAccount() : "N/A",
                tx != null ? tx.getReceiverAccount() : "N/A",
                featuresText
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Structure request body for Gemini API
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", prompt);

            Map<String, Object> partsMap = new HashMap<>();
            partsMap.put("parts", List.of(textPart));

            Map<String, Object> contentsMap = new HashMap<>();
            contentsMap.put("contents", List.of(partsMap));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(contentsMap, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List candidates = (List) response.getBody().get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map candidate = (Map) candidates.get(0);
                    Map content = (Map) candidate.get("content");
                    List parts = (List) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        Map part = (Map) parts.get(0);
                        return (String) part.get("text");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to call Gemini API: " + e.getMessage());
        }

        return generateMockReport(alert, tx, explanations);
    }
}
