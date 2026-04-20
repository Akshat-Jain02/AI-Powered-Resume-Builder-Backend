#!/bin/bash
# Run tests for all microservices and generate coverage reports
# Usage: ./run-all-tests.sh

set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICES=(
  "auth-service"
  "payment-service"
  "resume-service"
  "template-service"
  "section-service"
  "ai-service"
  "job-service"
  "notification-service"
)

PASS=0
FAIL=0
SKIP=0

echo ""
echo "=================================================="
echo "   ResumeAI — Running Tests for All Services"
echo "=================================================="
echo ""

for svc in "${SERVICES[@]}"; do
  SVC_DIR="$BASE_DIR/$svc"
  if [ ! -f "$SVC_DIR/pom.xml" ]; then
    echo "⚠️  SKIP: $svc (no pom.xml)"
    ((SKIP++))
    continue
  fi

  echo "🔍 Testing: $svc"
  cd "$SVC_DIR"

  # Run tests (skip jacoco-check so partial coverage doesn't break the script)
  if mvn clean test -Djacoco.skip=false -q 2>&1 | tail -5; then
    echo "✅  PASS: $svc"
    echo "   Coverage report: $SVC_DIR/target/site/jacoco/index.html"
    ((PASS++))
  else
    echo "❌  FAIL: $svc"
    ((FAIL++))
  fi
  echo ""
done

echo "=================================================="
echo "   Results: $PASS passed, $FAIL failed, $SKIP skipped"
echo "=================================================="
