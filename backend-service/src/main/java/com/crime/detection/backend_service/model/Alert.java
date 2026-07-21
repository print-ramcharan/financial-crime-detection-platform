package com.crime.detection.backend_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    private String id;
    private String transactionId;
    private String severity; // e.g., HIGH, MEDIUM, LOW
    private String reason;
    private String status;   // e.g., OPEN, ESCALATED, CLOSED
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
