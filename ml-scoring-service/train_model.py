import xgboost as xgb
import pandas as pd
import numpy as np
import os
import hashlib

def hash_mismatch(name):
    # Deterministic mapping of name to a simulated country mismatch (0 or 100)
    h = int(hashlib.md5(name.encode('utf-8')).hexdigest(), 16)
    return 100.0 if (h % 3 == 0) else 0.0

def load_and_engineer_data():
    csv_path = "../dataset/PS_20174392719_1491204439457_log.csv"
    if not os.path.exists(csv_path):
        # Fallback if file path differs
        csv_path = "dataset/PS_20174392719_1491204439457_log.csv"
        
    print(f"Loading dataset from {csv_path}...")
    
    # Read first 200,000 rows to keep training fast and memory footprint low
    df = pd.read_csv(csv_path, nrows=200000)
    
    print("Engineering features matching the API schema...")
    
    # Feature 1: Amount directly
    amount = df['amount']
    
    # Feature 2: Velocity Spike (amount relative to sender's starting balance)
    velocity_spike = (df['amount'] / (df['oldbalanceOrg'] + 1.0)) * 100.0
    # Cap it to a reasonable percentage range (0-100)
    velocity_spike = np.clip(velocity_spike, 0, 100)
    
    # Feature 3: New Beneficiary (Destination account had no previous balance history)
    new_beneficiary = np.where((df['oldbalanceDest'] == 0.0) & (df['newbalanceDest'] == 0.0), 1.0, 0.0)
    
    # Feature 4: Country Mismatch (Simulated deterministically using receiver account name hash)
    country_mismatch = df['nameDest'].apply(hash_mismatch)
    
    # Target label: isFraud
    y = df['isFraud'].values
    
    # Create final training DataFrame
    X = pd.DataFrame({
        "velocity_spike": velocity_spike,
        "country_mismatch": country_mismatch,
        "new_beneficiary": new_beneficiary,
        "amount": amount
    })
    
    return X, y

def train_and_save():
    try:
        X, y = load_and_engineer_data()
        
        print(f"Dataset loaded. Total rows: {len(X)} | Fraud cases: {sum(y)}")
        print("Training XGBoost model on engineered features...")
        
        # Calculate scale_pos_weight to handle class imbalance
        ratio = (len(y) - sum(y)) / (sum(y) + 1)
        
        dtrain = xgb.DMatrix(X, label=y)
        
        params = {
            'max_depth': 5,
            'eta': 0.1,
            'objective': 'binary:logistic',
            'eval_metric': 'auc',
            'scale_pos_weight': ratio
        }
        
        model = xgb.train(params, dtrain, num_boost_round=100)
        
        os.makedirs("models", exist_ok=True)
        model_path = "models/xgboost_model.json"
        model.save_model(model_path)
        print(f"Model successfully trained on PaySim dataset and saved to {model_path}!")
        
    except Exception as e:
        print(f"Error during training: {e}")

if __name__ == "__main__":
    train_and_save()
