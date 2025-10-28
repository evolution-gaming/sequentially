#!/bin/bash

# Sequentially Benchmark Comparison Script
# This script runs benchmarks for all Sequentially implementations and generates a comparison report

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  Sequentially Implementations Benchmark${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Create results directory with timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULTS_DIR="${PROJECT_DIR}/benchmark-results/${TIMESTAMP}"
mkdir -p "${RESULTS_DIR}"

echo -e "${YELLOW}Results will be saved to: ${RESULTS_DIR}${NC}"
echo ""

# Benchmark configurations
WARMUP_ITERATIONS=5
MEASUREMENT_ITERATIONS=10
FORKS=2
THREADS=1

echo -e "${GREEN}Benchmark Configuration:${NC}"
echo "  - Warmup iterations: ${WARMUP_ITERATIONS}"
echo "  - Measurement iterations: ${MEASUREMENT_ITERATIONS}"
echo "  - Forks: ${FORKS}"
echo "  - Threads: ${THREADS}"
echo ""

# Run benchmarks
echo -e "${YELLOW}Running benchmarks... (this will take several minutes)${NC}"
echo ""

sbt "project benchmark" "Jmh/run \
  -i ${MEASUREMENT_ITERATIONS} \
  -wi ${WARMUP_ITERATIONS} \
  -f ${FORKS} \
  -t ${THREADS} \
  -rf json \
  -rff ${RESULTS_DIR}/results.json \
  Sequentially.*Benchmark" | tee "${RESULTS_DIR}/raw-output.txt"

echo ""
echo -e "${GREEN}✓ Benchmarks completed!${NC}"
echo ""

# Parse and display results
echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}           Benchmark Results${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# Extract results from JSON (if jq is available)
if command -v jq &> /dev/null; then
    echo -e "${GREEN}Processing results...${NC}"
    echo ""
    
    # Create summary report
    SUMMARY_FILE="${RESULTS_DIR}/summary.txt"
    
    echo "Sequentially Implementation Benchmark Results" > "${SUMMARY_FILE}"
    echo "Generated: $(date)" >> "${SUMMARY_FILE}"
    echo "============================================" >> "${SUMMARY_FILE}"
    echo "" >> "${SUMMARY_FILE}"
    
    jq -r '.[] | 
        "Benchmark: \(.benchmark)
Mode: \(.mode)
Score: \(.primaryMetric.score) ± \(.primaryMetric.scoreError) \(.primaryMetric.scoreUnit)
Samples: \(.primaryMetric.scorePercentiles."0.0") (min) - \(.primaryMetric.scorePercentiles."100.0") (max)
----------------------------------------"' \
        "${RESULTS_DIR}/results.json" | tee -a "${SUMMARY_FILE}"
    
    echo "" | tee -a "${SUMMARY_FILE}"
    echo "============================================" | tee -a "${SUMMARY_FILE}"
    echo "Comparison (higher is better for throughput):" | tee -a "${SUMMARY_FILE}"
    echo "============================================" | tee -a "${SUMMARY_FILE}"
    
    # Sort by score descending
    jq -r '.[] | "\(.primaryMetric.score)\t\(.benchmark)"' "${RESULTS_DIR}/results.json" | \
        sort -rn | \
        nl | \
        tee -a "${SUMMARY_FILE}"
    
    echo ""
    echo -e "${GREEN}✓ Summary saved to: ${SUMMARY_FILE}${NC}"
else
    echo -e "${YELLOW}⚠ jq not found. Install jq for detailed analysis: brew install jq${NC}"
    echo -e "${YELLOW}Raw results available in: ${RESULTS_DIR}/results.json${NC}"
fi

echo ""
echo -e "${BLUE}============================================${NC}"
echo -e "${GREEN}All results saved to: ${RESULTS_DIR}${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""
echo "Files generated:"
echo "  - results.json      : JMH raw results"
echo "  - summary.txt       : Human-readable summary"
echo "  - raw-output.txt    : Complete console output"
echo ""
echo -e "${GREEN}Done! 🎉${NC}"

