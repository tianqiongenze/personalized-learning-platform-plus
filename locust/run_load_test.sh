#!/bin/bash
# Locust Load Test Runner for Personalized Learning Platform Plus
# Supports 5000 concurrent users with distributed workers

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
TARGET_HOST="${TARGET_HOST:-http://platform.local}"
LOCUST_MODE="${LOCUST_MODE:-master}"
LOCUST_MASTER_HOST="${LOCUST_MASTER_HOST:-locust-master}"
USERS="${USERS:-5000}"
SPAWN_RATE="${SPAWN_RATE:-50}"
RUN_TIME="${RUN_TIME:-30m}"
HEADLESS="${HEADLESS:-true}"
WEB_PORT="${WEB_PORT:-8089}"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Personalized Learning Platform Plus - Load Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${YELLOW}Target Host: $TARGET_HOST${NC}"
echo -e "${YELLOW}Mode: $LOCUST_MODE${NC}"
echo -e "${YELLOW}Users: $USERS${NC}"
echo -e "${YELLOW}Spawn Rate: $SPAWN_RATE users/sec${NC}"
echo -e "${YELLOW}Run Time: $RUN_TIME${NC}"
echo -e "${BLUE}========================================${NC}"

# Function to run master
run_master() {
    echo -e "${GREEN}Starting Locust Master...${NC}"
    
    CMD="locust -f /mnt/locust/locustfile.py \
        --master \
        --host=$TARGET_HOST \
        --web-host=0.0.0.0 \
        --web-port=$WEB_PORT \
        --expect-workers=4 \
        --worker-timeout=60"
    
    if [ "$HEADLESS" = "true" ]; then
        CMD="$CMD --headless -u $USERS -r $SPAWN_RATE --run-time $RUN_TIME"
    fi
    
    echo -e "${YELLOW}Running: $CMD${NC}"
    exec $CMD
}

# Function to run worker
run_worker() {
    echo -e "${GREEN}Starting Locust Worker...${NC}"
    
    CMD="locust -f /mnt/locust/locustfile.py \
        --worker \
        --master-host=$LOCUST_MASTER_HOST \
        --master-port=5557"
    
    echo -e "${YELLOW}Running: $CMD${NC}"
    exec $CMD
}

# Function to run standalone (single process)
run_standalone() {
    echo -e "${GREEN}Starting Locust Standalone...${NC}"
    
    CMD="locust -f /mnt/locust/locustfile.py \
        --host=$TARGET_HOST \
        --web-host=0.0.0.0 \
        --web-port=$WEB_PORT"
    
    if [ "$HEADLESS" = "true" ]; then
        CMD="$CMD --headless -u $USERS -r $SPAWN_RATE --run-time $RUN_TIME"
    fi
    
    echo -e "${YELLOW}Running: $CMD${NC}"
    exec $CMD
}

# Main
case "$LOCUST_MODE" in
    master)
        run_master
        ;;
    worker)
        run_worker
        ;;
    standalone)
        run_standalone
        ;;
    *)
        echo -e "${RED}Unknown mode: $LOCUST_MODE${NC}"
        echo "Usage: LOCUST_MODE=[master|worker|standalone] $0"
        exit 1
        ;;
esac