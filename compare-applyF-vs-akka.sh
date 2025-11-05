#!/bin/bash

# Compare SequentiallyCats.applyF vs Akka-based Sequentially

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  SequentiallyCats.applyF vs Akka${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="${PROJECT_DIR}/benchmark-results/applyF-vs-akka-${TIMESTAMP}"
mkdir -p "${RESULTS_DIR}"

echo -e "${YELLOW}Running comprehensive comparison...${NC}"
echo ""
echo -e "This benchmark compares:"
echo -e "  1. ${CYAN}Akka (Future-based)${NC} - Original implementation"
echo -e "  2. ${CYAN}Cats.applyF (F-based)${NC} - New pure functional method"
echo -e "  3. ${CYAN}Cats.apply (Future)${NC} - Cats with Future conversion"
echo ""

sbt "project benchmark" "Jmh/run \
  -i 5 \
  -wi 5 \
  -f 1 \
  -t 1 \
  -rf json \
  -rff ${RESULTS_DIR}/results.json \
  SequentiallyCatsVsAkkaBenchmark" | tee "${RESULTS_DIR}/output.txt"

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}           Results Summary${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

if command -v jq &> /dev/null; then
    # Extract scores (using first match only to avoid multiple values)
    AKKA_SCORE=$(jq -r '[.[] | select(.benchmark | endswith("akkaFuture"))] | first | .primaryMetric.score' "${RESULTS_DIR}/results.json")
    AKKA_ERROR=$(jq -r '[.[] | select(.benchmark | endswith("akkaFuture"))] | first | .primaryMetric.scoreError' "${RESULTS_DIR}/results.json")
    
    CATS_F_SCORE=$(jq -r '[.[] | select(.benchmark | endswith("catsApplyF"))] | first | .primaryMetric.score' "${RESULTS_DIR}/results.json")
    CATS_F_ERROR=$(jq -r '[.[] | select(.benchmark | endswith("catsApplyF"))] | first | .primaryMetric.scoreError' "${RESULTS_DIR}/results.json")
    
    CATS_FUT_SCORE=$(jq -r '[.[] | select(.benchmark | endswith("catsApplyFuture"))] | first | .primaryMetric.score' "${RESULTS_DIR}/results.json")
    CATS_FUT_ERROR=$(jq -r '[.[] | select(.benchmark | endswith("catsApplyFuture"))] | first | .primaryMetric.scoreError' "${RESULTS_DIR}/results.json")
    
    echo -e "${GREEN}1. Akka (Future-based):${NC}"
    printf "   Score: %.2f ± %.2f ops/s\n" "$AKKA_SCORE" "$AKKA_ERROR"
    echo ""
    
    echo -e "${GREEN}2. Cats applyF (F[T]):${NC}"
    printf "   Score: %.2f ± %.2f ops/s\n" "$CATS_F_SCORE" "$CATS_F_ERROR"
    echo ""
    
    echo -e "${GREEN}3. Cats apply (Future[T]):${NC}"
    printf "   Score: %.2f ± %.2f ops/s\n" "$CATS_FUT_SCORE" "$CATS_FUT_ERROR"
    echo ""
    
    echo -e "${BLUE}----------------------------------------${NC}"
    echo -e "${CYAN}Performance Comparisons:${NC}"
    echo -e "${BLUE}----------------------------------------${NC}"
    
    # Cats.applyF vs Akka
    IMPROVEMENT_F=$(awk "BEGIN {printf \"%.1f\", ($CATS_F_SCORE / $AKKA_SCORE - 1) * 100}")
    if (( $(echo "$IMPROVEMENT_F > 0" | bc -l) )); then
        echo -e "${GREEN}✓ Cats.applyF is ${IMPROVEMENT_F}% FASTER than Akka${NC}"
    else
        ABS_DIFF=$(echo "$IMPROVEMENT_F" | tr -d -)
        echo -e "${RED}✗ Cats.applyF is ${ABS_DIFF}% slower than Akka${NC}"
    fi
    
    # Cats.apply vs Akka
    IMPROVEMENT_FUT=$(awk "BEGIN {printf \"%.1f\", ($CATS_FUT_SCORE / $AKKA_SCORE - 1) * 100}")
    if (( $(echo "$IMPROVEMENT_FUT > 0" | bc -l) )); then
        echo -e "${GREEN}✓ Cats.apply is ${IMPROVEMENT_FUT}% FASTER than Akka${NC}"
    else
        ABS_DIFF=$(echo "$IMPROVEMENT_FUT" | tr -d -)
        echo -e "${YELLOW}⚠ Cats.apply is ${ABS_DIFF}% slower than Akka${NC}"
    fi
    
    # Cats.applyF vs Cats.apply
    IMPROVEMENT_INTERNAL=$(awk "BEGIN {printf \"%.1f\", ($CATS_F_SCORE / $CATS_FUT_SCORE - 1) * 100}")
    echo -e "${CYAN}• Cats.applyF vs Cats.apply: ${IMPROVEMENT_INTERNAL}% improvement${NC}"
    
    echo -e "${BLUE}----------------------------------------${NC}"
    
    # Create summary report
    REPORT="${RESULTS_DIR}/summary.txt"
    {
        echo "SequentiallyCats.applyF vs Akka Comparison"
        echo "Generated: $(date)"
        echo "========================================"
        echo ""
        echo "1. Akka (Future-based):"
        printf "   Score: %.2f ± %.2f ops/s\n" "$AKKA_SCORE" "$AKKA_ERROR"
        echo ""
        echo "2. Cats applyF (F[T]):"
        printf "   Score: %.2f ± %.2f ops/s\n" "$CATS_F_SCORE" "$CATS_F_ERROR"
        echo ""
        echo "3. Cats apply (Future[T]):"
        printf "   Score: %.2f ± %.2f ops/s\n" "$CATS_FUT_SCORE" "$CATS_FUT_ERROR"
        echo ""
        echo "Performance Differences:"
        echo "  - Cats.applyF vs Akka: ${IMPROVEMENT_F}%"
        echo "  - Cats.apply vs Akka: ${IMPROVEMENT_FUT}%"
        echo "  - Cats.applyF vs Cats.apply: ${IMPROVEMENT_INTERNAL}%"
    } > "$REPORT"
    
    echo ""
    echo -e "${GREEN}Report saved to: ${REPORT}${NC}"
else
    echo -e "${YELLOW}⚠ Install jq for detailed analysis: brew install jq${NC}"
    echo -e "Results saved in: ${RESULTS_DIR}${NC}"
fi

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}Recommendation:${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo -e "For ${CYAN}pure Cats Effect${NC} applications:"
echo -e "  → Use ${GREEN}applyF${NC} for best performance"
echo ""
echo -e "For ${CYAN}Future-based${NC} interop:"
echo -e "  → Use ${YELLOW}apply${NC} (both Akka and Cats work)"
echo ""
echo -e "${GREEN}Done! 🎉${NC}"

