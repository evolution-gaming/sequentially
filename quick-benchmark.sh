#!/bin/bash

# Quick benchmark for development/testing
# Uses fewer iterations for faster results

set -e

YELLOW='\033[1;33m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}Quick Benchmark (fewer iterations for speed)${NC}"
echo ""

# Quick benchmark settings
WARMUP=2
ITERATIONS=5
FORKS=1

echo -e "${YELLOW}Running quick comparison...${NC}"
echo ""

sbt "project benchmark" "Jmh/run \
  -i ${ITERATIONS} \
  -wi ${WARMUP} \
  -f ${FORKS} \
  -t 1 \
  Sequentially.*Benchmark"

echo ""
echo -e "${GREEN}Quick benchmark complete!${NC}"
echo ""
echo -e "${YELLOW}Note: For accurate results, use ./run-benchmarks.sh${NC}"

