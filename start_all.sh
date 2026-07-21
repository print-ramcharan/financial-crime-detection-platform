#!/bin/bash
# start_all.sh

echo "Starting all services for Crime Detection Platform..."
cd "$(dirname "$0")"

# Start Docker containers (Kafka, Zookeeper, Postgres)
echo "Starting Docker containers..."
docker-compose up -d

echo "Waiting for Kafka and Postgres to be ready (10s)..."
sleep 10

# Start backend service
echo "Starting Backend Service on port 8080..."
cd backend-service
./mvnw spring-boot:run > backend.log 2>&1 &
BACKEND_PID=$!
cd ..

# Start ML scoring service
echo "Starting ML Scoring Service..."
cd ml-scoring-service
source venv/bin/activate
# Start the FastAPI ML app
uvicorn app.main:app --port 5001 > ml_scoring.log 2>&1 &
ML_PID=$!
cd ..

# Start Frontend
echo "Starting Frontend Dashboard on port 3000..."
cd frontend-dashboard
npm run dev > frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..

echo "==========================================="
echo "All services started successfully!"
echo "Backend PID: $BACKEND_PID"
echo "ML Service PID: $ML_PID"
echo "Frontend PID: $FRONTEND_PID"
echo "==========================================="
echo "To view logs, you can run in a separate terminal:"
echo "tail -f backend-service/backend.log"
echo "tail -f ml-scoring-service/ml_scoring.log"
echo "tail -f frontend-dashboard/frontend.log"
echo "==========================================="
echo "Press Ctrl+C here to stop all services."

# Trap Ctrl+C to kill all background processes
trap "echo 'Stopping all services...'; kill $BACKEND_PID $ML_PID $FRONTEND_PID; docker-compose down; exit" SIGINT SIGTERM

# Wait indefinitely to keep the script running
wait
