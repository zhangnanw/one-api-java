#!/bin/bash
set -e

# ============================================================
# one-api-java Linux deploy script
# Usage: ./deploy.sh [--skip-tests]
# ============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SKIP_TESTS=false
if [ "$1" = "--skip-tests" ]; then SKIP_TESTS=true; fi

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[DEPLOY]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; exit 1; }

DEPLOY_DIR="$HOME/.one-api"
JAR_SRC="$SCRIPT_DIR/target/one-api-java-1.0.0.jar"
JAR_DST="$DEPLOY_DIR/one-api-java.jar"
LOG_FILE="$DEPLOY_DIR/server.log"
HEALTH_URL="http://localhost:13000/api/status"
PORT=13000

# ======== Step 1: Check Java ========
log "Step 1/4: Check Java..."
if ! command -v java &>/dev/null; then err "Java not found. Install JDK 17+."; fi
JAVA_VER=$(java -version 2>&1 | head -1)
log "$JAVA_VER"

# ======== Step 2: Build ========
log "Step 2/4: Build & package..."

if [ ! -x "./mvnw" ]; then chmod +x mvnw; fi
MVN_ARGS="clean package --batch-mode"
if $SKIP_TESTS; then MVN_ARGS="$MVN_ARGS -DskipTests"; fi

./mvnw $MVN_ARGS
if [ $? -ne 0 ]; then err "Build failed"; fi
if [ ! -f "$JAR_SRC" ]; then err "JAR not found: $JAR_SRC"; fi

JAR_SIZE=$(du -h "$JAR_SRC" | cut -f1)
log "Build OK ($JAR_SIZE)"

# ======== Step 3: Stop old process ========
log "Step 3/4: Stop old process..."

OLD_PID=$(pgrep -f "one-api-java.jar" 2>/dev/null || true)
if [ -z "$OLD_PID" ]; then
    OLD_PID=$(ss -tlnp 2>/dev/null | grep ":$PORT " | grep -oP 'pid=\K\d+' | head -1 || true)
fi
if [ -n "$OLD_PID" ]; then
    log "Stopping PID $OLD_PID..."
    kill "$OLD_PID" 2>/dev/null || true
    sleep 3
fi
log "Old process cleared"

# ======== Step 4: Deploy & start ========
log "Step 4/4: Deploy JAR..."

mkdir -p "$DEPLOY_DIR"

if [ -f "$JAR_DST" ]; then
    BAK="$JAR_DST.bak.$(date +%Y%m%d%H%M%S)"
    cp "$JAR_DST" "$BAK"
    log "Backed up -> $BAK"
fi

cp "$JAR_SRC" "$JAR_DST"
log "JAR copied -> $JAR_DST"

cd "$DEPLOY_DIR"
nohup java -Dfile.encoding=UTF-8 -jar "$JAR_DST" > "$LOG_FILE" 2>&1 &
NEW_PID=$!
log "Started (PID: $NEW_PID)"

log "Waiting for startup..."
HEALTHY=false
for i in $(seq 1 15); do
    sleep 2
    if ! kill -0 "$NEW_PID" 2>/dev/null; then
        err "Process exited early. Log:\n$(tail -20 "$LOG_FILE")"
    fi
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$HEALTH_URL" 2>/dev/null || true)
    if [ "$HTTP_CODE" = "200" ]; then HEALTHY=true; break; fi
    echo -n "."
done
echo ""

if $HEALTHY; then
    log "Health check OK (HTTP 200)"
    echo ""
    echo -e "${GREEN}============================================${NC}"
    echo -e "${GREEN}  Deploy Complete${NC}"
    echo -e "${GREEN}  URL: $HEALTH_URL${NC}"
    echo -e "${GREEN}  Log: $LOG_FILE${NC}"
    echo -e "${GREEN}============================================${NC}"
else
    err "Health check failed after 30s. Log:\n$(tail -20 "$LOG_FILE")"
fi
