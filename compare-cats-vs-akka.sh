#!/bin/bash

# Compare SequentiallyCats vs Original Akka implementation
# This is the most important comparison to see if the new implementation is better

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  SequentiallyCats vs Akka Comparison${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="${PROJECT_DIR}/benchmark-results/comparison-${TIMESTAMP}"
mkdir -p "${RESULTS_DIR}"

echo -e "${YELLOW}Benchmarking SequentiallyCats (Cats Effect + MapRef)...${NC}"
echo ""

sbt "project benchmark" "Jmh/run \
  -i 10 \
  -wi 5 \
  -f 2 \
  -t 1 \
  -rf json \
  -rff ${RESULTS_DIR}/cats-results.json \
  SequentiallyCatsBenchmark"

echo ""
echo -e "${YELLOW}Benchmarking Sequentially (Original Akka)...${NC}"
echo ""

sbt "project benchmark" "Jmh/run \
  -i 10 \
  -wi 5 \
  -f 2 \
  -t 1 \
  -rf json \
  -rff ${RESULTS_DIR}/akka-results.json \
  SequentiallyBenchmark.apply"

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}           Comparison Results${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

if command -v jq &> /dev/null; then
    CATS_SCORE=$(jq -r '.[0].primaryMetric.score' "${RESULTS_DIR}/cats-results.json")
    CATS_ERROR=$(jq -r '.[0].primaryMetric.scoreError' "${RESULTS_DIR}/cats-results.json")
    CATS_UNIT=$(jq -r '.[0].primaryMetric.scoreUnit' "${RESULTS_DIR}/cats-results.json")
    
    AKKA_SCORE=$(jq -r '.[0].primaryMetric.score' "${RESULTS_DIR}/akka-results.json")
    AKKA_ERROR=$(jq -r '.[0].primaryMetric.scoreError' "${RESULTS_DIR}/akka-results.json")
    AKKA_UNIT=$(jq -r '.[0].primaryMetric.scoreUnit' "${RESULTS_DIR}/akka-results.json")
    
    echo -e "${GREEN}SequentiallyCats (Cats Effect):${NC}"
    printf "  Score: %.2f ± %.2f %s\n" "$CATS_SCORE" "$CATS_ERROR" "$CATS_UNIT"
    echo ""
    
    echo -e "${GREEN}Sequentially (Akka):${NC}"
    printf "  Score: %.2f ± %.2f %s\n" "$AKKA_SCORE" "$AKKA_ERROR" "$AKKA_UNIT"
    echo ""
    
    # Calculate percentage difference
    IMPROVEMENT=$(awk "BEGIN {printf \"%.2f\", ($CATS_SCORE / $AKKA_SCORE - 1) * 100}")
    
    echo -e "${BLUE}----------------------------------------${NC}"
    if (( $(echo "$IMPROVEMENT > 0" | bc -l) )); then
        echo -e "${GREEN}✓ SequentiallyCats is ${IMPROVEMENT}% FASTER${NC}"
    elif (( $(echo "$IMPROVEMENT < 0" | bc -l) )); then
        ABS_IMPROVEMENT=$(echo "$IMPROVEMENT" | tr -d -)
        echo -e "${YELLOW}⚠ SequentiallyCats is ${ABS_IMPROVEMENT}% slower${NC}"
    else
        echo -e "Both implementations have similar performance"
    fi
    echo -e "${BLUE}----------------------------------------${NC}"
    
    # Save comparison report
    REPORT="${RESULTS_DIR}/comparison-report.txt"
    {
        echo "SequentiallyCats vs Akka Comparison"
        echo "Generated: $(date)"
        echo "========================================"
        echo ""
        echo "SequentiallyCats (Cats Effect + MapRef):"
        printf "  Score: %.2f ± %.2f %s\n" "$CATS_SCORE" "$CATS_ERROR" "$CATS_UNIT"
        echo ""
        echo "Sequentially (Original Akka):"
        printf "  Score: %.2f ± %.2f %s\n" "$AKKA_SCORE" "$AKKA_ERROR" "$AKKA_UNIT"
        echo ""
        echo "Performance Difference: ${IMPROVEMENT}%"
        echo ""
        echo "Higher score = better (more operations per second)"
    } > "$REPORT"
    
    echo ""
    echo -e "${GREEN}Report saved to: ${REPORT}${NC}"
else
    echo -e "${YELLOW}⚠ Install jq for detailed comparison: brew install jq${NC}"
    echo -e "Results saved in: ${RESULTS_DIR}${NC}"
fi

echo ""
echo -e "${GREEN}Done! 🎉${NC}"

