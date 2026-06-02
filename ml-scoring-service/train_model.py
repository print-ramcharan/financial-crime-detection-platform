import xgboost as xgb
import pandas as pd
import numpy as np
import os

def create_dummy_data():
    # 1000 samples, 4 features
    # features: velocity_spike, country_mismatch, new_beneficiary, amount
    np.random.seed(42)
    
    # Normal transactions
    normal_tx = np.random.rand(800, 4) * [20, 10, 0.1, 1000]
    normal_labels = np.zeros(800)
    
    # Suspicious transactions (higher values for our features)
    suspicious_tx = np.random.rand(200, 4) * [100, 100, 1.0, 5000] + [50, 50, 0.5, 2000]
    suspicious_labels = np.ones(200)
    
    X = np.vstack([normal_tx, suspicious_tx])
    y = np.concatenate([normal_labels, suspicious_labels])
    
    df = pd.DataFrame(X, columns=["velocity_spike", "country_mismatch", "new_beneficiary", "amount"])
    return df, y

def train_and_save():
    print("Generating dummy training data...")
    X, y = create_dummy_data()
    
    print("Training XGBoost model...")
    dtrain = xgb.DMatrix(X, label=y)
    
    params = {
        'max_depth': 4,
        'eta': 0.1,
        'objective': 'binary:logistic',
        'eval_metric': 'auc'
    }
    
    model = xgb.train(params, dtrain, num_boost_round=50)
    
    os.makedirs("models", exist_ok=True)
    model_path = "models/xgboost_model.json"
    model.save_model(model_path)
    print(f"Model saved to {model_path}")

if __name__ == "__main__":
    train_and_save()
