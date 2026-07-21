package com.crime.detection.backend_service.model;

import jakarta.persistence.*;

@Entity
@Table(name = "explanations")
public class Explanation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String transactionId;
    private String featureName;
    private Double impactValue;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getFeatureName() { return featureName; }
    public void setFeatureName(String featureName) { this.featureName = featureName; }
    public Double getImpactValue() { return impactValue; }
    public void setImpactValue(Double impactValue) { this.impactValue = impactValue; }
}
