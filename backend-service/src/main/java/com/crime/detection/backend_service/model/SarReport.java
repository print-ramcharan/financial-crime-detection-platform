package com.crime.detection.backend_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sar_reports")
public class SarReport {
    @Id
    private String id;
    private String alertId;
    @Column(columnDefinition = "TEXT")
    private String reportText;
    private LocalDateTime generatedAt;
    private String reviewStatus; // e.g., PENDING, APPROVED, REJECTED

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getReportText() { return reportText; }
    public void setReportText(String reportText) { this.reportText = reportText; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
}
