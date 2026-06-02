# Real-Time Financial Crime Detection Platform

## Project Overview

A production-style financial crime detection platform capable of ingesting transaction events in real time, detecting suspicious activity using machine learning and graph analytics, explaining every decision using Explainable AI (XAI), enforcing deterministic compliance rules, and automatically generating regulator-ready Suspicious Activity Reports (SARs).

The goal is not merely fraud classification but the creation of an end-to-end compliance workflow similar to what exists inside banks, fintech companies, payment processors, and cryptocurrency exchanges.

---

## Business Problem

Financial institutions process millions of transactions daily. Traditional rule-based systems generate excessive false positives while black-box machine learning systems often fail regulatory audits because investigators cannot explain why a transaction was flagged.

The platform solves both problems by combining:
1. Machine Learning Risk Scoring
2. Explainable AI
3. Deterministic Compliance Rules
4. Graph-Based Relationship Analysis
5. Automated Regulatory Reporting

Every suspicious transaction has:
* Risk score
* Explanation
* Rule violations
* Network analysis
* Audit trail
* Generated SAR report

---

## Core Objectives

### Objective 1: Detect suspicious transactions using machine learning.
Examples:
* Unusual transaction velocity
* Rapid fund movement
* Structuring
* Geographic anomalies
* Abnormal transfer frequency

### Objective 2: Explain every model decision.
Investigators should understand exactly why a transaction was flagged (No black-box predictions).
```text
Risk Score: 92%

Top Contributors:
Velocity Spike: +41%
Country Mismatch: +29%
New Beneficiary: +15%
```

### Objective 3: Apply deterministic compliance policies.
Rules must always override AI decisions.
```text
IF Risk Score > 85 AND Country Mismatch > 25% THEN Freeze Transaction
```

### Objective 4: Discover suspicious account networks.
Detect money circles, mule networks, layering chains, shared beneficiaries, and rapid fund cycling using graph analysis.

### Objective 5: Generate Suspicious Activity Reports automatically.
Generate structured SAR drafts using an LLM (Gemini API) instead of manual investigator reports.

---

## System Architecture

```text
Transaction Generator
        |
        v
Apache Kafka
        |
        v
Feature Engineering Service
        |
        v
Risk Scoring Service (XGBoost)
        |
        v
SHAP Explainability Service
        |
        v
Compliance Rule Engine
        |
        v
Graph Analysis Engine
        |
        v
Decision Service
        |
        +--------------------+
        |                    |
        v                    v
 Allow Transaction     Hold/Review
                             |
                             v
                      SAR Generation
                             |
                             v
                     Compliance Dashboard
```

---

## Technology Stack

### Backend
* **Spring Boot**: REST APIs, Rule engine, Workflow orchestration, Audit logging, Dashboard APIs.

### Event Streaming
* **Apache Kafka**: Topics (`transactions`, `risk-scores`, `alerts`, `sar-reports`, `audit-events`).

### Machine Learning
* **Python**: XGBoost, Pandas, NumPy, Scikit-learn, SHAP.
* **Responsibilities**: Model training, Risk prediction, Explanation generation.

### Graph Analysis
* **NetworkX** (Initially), migrating to **Neo4j** / **Apache Spark GraphX**.
* **Responsibilities**: Relationship discovery, Community detection, Cycle detection, Layering identification.

### Database
* **PostgreSQL**: Stores accounts, transactions, risk_scores, shap_explanations, alerts, sar_reports, audit_logs.

### LLM Layer
* **Gemini API**: SAR generation, Investigator summaries, Alert explanation narratives.

---

## Data Model

* **Account**: `id`, `account_number`, `customer_name`, `country`, `risk_rating`, `created_at`
* **Transaction**: `id`, `sender_account`, `receiver_account`, `amount`, `currency`, `country`, `timestamp`, `status`
* **Risk Score**: `id`, `transaction_id`, `score`, `model_version`, `prediction_time`
* **SHAP Explanation**: `id`, `transaction_id`, `feature_name`, `impact_value`
* **Alert**: `id`, `transaction_id`, `severity`, `reason`, `status`, `created_at`
* **SAR Report**: `id`, `alert_id`, `report_text`, `generated_at`, `review_status`

---

## Feature Engineering

* **Velocity Features**: Transactions last hour/day, Average amount, Amount deviation
* **Geographic Features**: Country mismatch, Foreign transfer frequency, Travel pattern anomalies
* **Behavioral Features**: New beneficiary count, Account age, Beneficiary diversity
* **Network Features**: Degree centrality, Betweenness centrality, Number of hops, Circular transfer count

---

## Machine Learning Pipeline

* **Dataset**: PaySim Dataset
* **Model**: XGBoostClassifier
* **Output**: Probability of suspicious activity (e.g., `{"risk_score": 0.92}`)

---

## Explainability Pipeline

Generates SHAP values stored in PostgreSQL. Every prediction must be explainable.
Example: `{"velocity_spike": 0.42, "country_mismatch": 0.31, "new_beneficiary": 0.15}`

---

## Compliance Rule Engine

Configurable rules stored in the database.
* **Rule 1**: IF Risk Score > 85 AND Amount > 10000 THEN HOLD
* **Rule 2**: IF Country Mismatch > 30% AND Velocity Spike > 40% THEN ESCALATE

---

## Graph Analysis Engine

Detects:
* **Circular Transfers**: Possible laundering loops.
* **Layering Chains**: Money movement through multiple hops (A -> B -> C -> D -> E).
* **Mule Networks**: Many accounts forwarding funds to one destination.
* **Community Detection**: Groups of suspiciously connected accounts.

---

## Dashboard Requirements

* **Transaction Stream**: Live incoming transactions.
* **Risk Dashboard**: Risk scores, High-risk accounts, Trends.
* **Alert Dashboard**: Open alerts, Closed alerts, Escalated cases.
* **Graph Visualization**: Interactive account network.
* **SAR Review Dashboard**: Compliance officers review generated reports.

---

## Audit Requirements

Every action must be logged (Transaction Received, Risk Calculated, SHAP Generated, Rule Triggered, Alert Created, SAR Generated, Investigator Reviewed).

---

## Future Enhancements (Phases 2 & 3)

* Neo4j graph database & Spark Streaming
* Graph Neural Networks
* Real transaction feeds
* Multi-model ensemble
* Sanctions screening & KYC integration
* Entity resolution & Adverse media search
