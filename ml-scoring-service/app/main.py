import os
import json
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from pydantic import BaseModel
import xgboost as xgb
import shap
import pandas as pd
import numpy as np
from kafka import KafkaConsumer, KafkaProducer

# Configuration
KAFKA_BROKER = os.getenv("KAFKA_BROKER", "localhost:9092")
TRANSACTIONS_TOPIC = "transactions"
RISK_SCORES_TOPIC = "risk-scores"

# Global state
model = None
explainer = None
producer = None

class Transaction(BaseModel):
    id: str
    sender_account: str
    receiver_account: str
    amount: float
    currency: str
    country: str
    timestamp: str

def load_model():
    global model, explainer
    model_path = "models/xgboost_model.json"
    if os.path.exists(model_path):
        model = xgb.Booster()
        model.load_model(model_path)
        print("Model loaded.")
        # Dummy background data for SHAP explainer
        X_background = pd.DataFrame(np.random.rand(10, 4), columns=["velocity_spike", "country_mismatch", "new_beneficiary", "amount"])
        explainer = shap.TreeExplainer(model, X_background, feature_perturbation='interventional')
    else:
        print("Model file not found. Please run train_model.py first.")

def extract_features(tx: dict) -> pd.DataFrame:
    # Dummy feature extraction based on the transaction
    # In a real system, this would query a feature store
    features = {
        "velocity_spike": np.random.rand() * 100,
        "country_mismatch": 100.0 if tx.get("country") == "HighRiskCountry" else 0.0,
        "new_beneficiary": 1.0,
        "amount": float(tx.get("amount", 0.0))
    }
    return pd.DataFrame([features])

async def process_transactions():
    global producer
    consumer = KafkaConsumer(
        TRANSACTIONS_TOPIC,
        bootstrap_servers=KAFKA_BROKER,
        value_deserializer=lambda m: json.loads(m.decode('utf-8')),
        auto_offset_reset='latest'
    )
    
    print(f"Listening for transactions on topic {TRANSACTIONS_TOPIC}...")
    
    # Run consumer in a background thread to avoid blocking asyncio loop
    loop = asyncio.get_running_loop()
    
    def consume():
        for message in consumer:
            tx = message.value
            if model and explainer:
                features_df = extract_features(tx)
                
                # Predict risk score
                dmatrix = xgb.DMatrix(features_df)
                prediction = model.predict(dmatrix)[0]
                
                # Generate SHAP values
                shap_values = explainer.shap_values(features_df)
                
                # Format explanations
                feature_names = features_df.columns
                impact_values = shap_values[0] if isinstance(shap_values, list) else shap_values
                if len(impact_values.shape) > 1:
                    impact_values = impact_values[0]
                    
                explanation = {str(k): float(v) for k, v in zip(feature_names, impact_values)}
                
                risk_event = {
                    "transaction_id": tx.get("id"),
                    "risk_score": float(prediction * 100),  # Scale to 0-100
                    "model_version": "xgb-v1.0",
                    "explanation": explanation
                }
                
                print(f"Scored Tx {tx.get('id')}: Score={risk_event['risk_score']:.2f}%")
                
                producer.send(RISK_SCORES_TOPIC, risk_event)
                producer.flush()

    await loop.run_in_executor(None, consume)


@asynccontextmanager
async def lifespan(app: FastAPI):
    global producer
    load_model()
    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BROKER,
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )
    
    # Start Kafka consumer background task
    asyncio.create_task(process_transactions())
    yield
    if producer:
        producer.close()

app = FastAPI(title="ML Scoring Service", lifespan=lifespan)

@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": model is not None}

@app.post("/score")
def score_transaction(tx: Transaction):
    if not model:
        return {"error": "Model not loaded"}
    
    features_df = extract_features(tx.dict())
    dmatrix = xgb.DMatrix(features_df)
    prediction = model.predict(dmatrix)[0]
    
    shap_values = explainer.shap_values(features_df)
    impact_values = shap_values[0] if isinstance(shap_values, list) else shap_values
    if len(impact_values.shape) > 1:
        impact_values = impact_values[0]
        
    explanation = {str(k): float(v) for k, v in zip(features_df.columns, impact_values)}
    
    return {
        "transaction_id": tx.id,
        "risk_score": float(prediction * 100),
        "explanation": explanation
    }
