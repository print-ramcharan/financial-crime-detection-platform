package com.crime.detection.backend_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "risk_scores")
public class RiskScore {
    @Id
    private String id;
    private String transactionId;
    private Double score;
    private String modelVersion;
    private LocalDateTime predictionTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public LocalDateTime getPredictionTime() { return predictionTime; }
    public void setPredictionTime(LocalDateTime predictionTime) { this.predictionTime = predictionTime; }
}
