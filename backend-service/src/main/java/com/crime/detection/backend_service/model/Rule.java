package com.crime.detection.backend_service.model;

import jakarta.persistence.*;

@Entity
@Table(name = "compliance_rules")
public class Rule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private String expression; // e.g., "riskScore > 85 && amount > 10000"
    private String action;     // HOLD, ESCALATE, GENERATE_ALERT
    private boolean active;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
