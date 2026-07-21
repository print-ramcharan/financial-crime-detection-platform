package com.crime.detection.backend_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    private String id;
    private String accountNumber;
    private String customerName;
    private String country;
    private String riskRating;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getRiskRating() { return riskRating; }
    public void setRiskRating(String riskRating) { this.riskRating = riskRating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
