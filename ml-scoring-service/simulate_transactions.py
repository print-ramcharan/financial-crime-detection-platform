import time
import requests
import pandas as pd
import os
import uuid

BACKEND_URL = "http://localhost:8080/api/transactions"

def send_transaction(sender, receiver, amount, country):
    payload = {
        "id": str(uuid.uuid4()),
        "senderAccount": sender,
        "receiverAccount": receiver,
        "amount": amount,
        "currency": "USD",
        "country": country
    }
    try:
        res = requests.post(BACKEND_URL, json=payload)
        if res.status_code == 200:
            print(f"Sent: {sender} -> {receiver} | Amount: ${amount} | Country: {country} | Status: {res.json().get('status')}")
        else:
            print(f"Failed to send: {res.status_code} - {res.text}")
    except Exception as e:
        print(f"Error connecting to backend: {e}")

if __name__ == "__main__":
    csv_path = "../dataset/PS_20174392719_1491204439457_log.csv"
    if not os.path.exists(csv_path):
        csv_path = "dataset/PS_20174392719_1491204439457_log.csv"
        
    if not os.path.exists(csv_path):
        print(f"Error: PaySim dataset CSV not found at {csv_path}")
        exit(1)
        
    print(f"Loading transaction simulator with real records from {csv_path}...")
    
    # Read the dataset in chunks or read first 1000 rows
    df = pd.read_csv(csv_path, nrows=1000)
    
    # Sort or shuffle so we see a good variety
    df_shuffled = df.sample(frac=1).reset_index(drop=True)
    
    print("Starting real-time streaming simulation (Ctrl+C to stop)...")
    
    for index, row in df_shuffled.iterrows():
        sender = str(row['nameOrig'])
        receiver = str(row['nameDest'])
        amount = float(row['amount'])
        
        # Simulate country mismatch
        # If destination name starts with 'M' (Merchant), let's mark it as US,
        # otherwise randomly flag some transfers to HighRiskCountry to test alerting rules
        is_merchant = receiver.startswith('M')
        country = "US" if is_merchant else ("HighRiskCountry" if index % 7 == 0 else "US")
        
        send_transaction(sender, receiver, amount, country)
        time.sleep(2)
