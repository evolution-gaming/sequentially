# Sequentially Benchmarks

This document explains how to run and compare benchmarks for different Sequentially implementations.

## Quick Start

### Full Benchmark (Recommended for final comparison)

```bash
./run-benchmarks.sh
```

This runs a comprehensive benchmark with:
- 5 warmup iterations
- 10 measurement iterations  
- 2 forks for statistical significance
- Results saved with timestamp in `benchmark-results/`

**Time:** ~10-15 minutes

### Quick Benchmark (For development)

```bash
./quick-benchmark.sh
```

Faster benchmark with fewer iterations for quick testing during development.

**Time:** ~3-5 minutes

## Implementations Being Compared

| Implementation | Description |
|----------------|-------------|
| `SequentiallyBenchmark` | Original Akka-based implementation using actors |
| `SequentiallyAsyncBenchmark` | Async stream-based version |
| `SequentiallyHandlerBenchmark` | Handler-based version |
| `SequentiallyStreamBenchmark` | Stream-based version |
| **`SequentiallyCatsBenchmark`** | **New Cats Effect + MapRef implementation** |

## Manual Benchmark Commands

### Run specific benchmark

```bash
sbt "project benchmark" "Jmh/run SequentiallyCatsBenchmark"
```

### Run with custom parameters

```bash
sbt "project benchmark" "Jmh/run -i 20 -wi 10 -f 3 -t 1 Sequentially.*"
```

### JMH Parameters

- `-i N` : Number of measurement iterations
- `-wi N` : Number of warmup iterations
- `-f N` : Number of forks
- `-t N` : Number of threads
- `-rf json` : Result format (json, csv, text)
- `-rff <file>` : Result output file

## Understanding Results

### Throughput Mode

Results show operations per second (ops/s):

```
Benchmark                           Mode  Cnt      Score       Error  Units
SequentiallyCatsBenchmark.apply    thrpt   10  75000.123 ± 1234.567  ops/s
SequentiallyBenchmark.apply        thrpt   10  50000.456 ± 2345.678  ops/s
```

**Higher score = Better performance**

### Score Components

- **Score**: Average throughput
- **Error**: Confidence interval (±)
- **Higher is better** for throughput mode

## Analyzing Results

### View Summary

After running `./run-benchmarks.sh`, check:

```bash
cat benchmark-results/<timestamp>/summary.txt
```

### View JSON Results (with jq)

```bash
# Install jq if not available
brew install jq  # macOS
apt install jq   # Linux

# View all scores
jq '.[] | {benchmark: .benchmark, score: .primaryMetric.score}' benchmark-results/<timestamp>/results.json

# Sort by performance
jq -r '.[] | "\(.primaryMetric.score)\t\(.benchmark)"' benchmark-results/<timestamp>/results.json | sort -rn
```

## Expected Performance Characteristics

### SequentiallyCats Advantages

✅ **Lock-free**: Uses `MapRef` with 128 shards  
✅ **No global bottleneck**: Each key has independent semaphore  
✅ **Efficient cleanup**: Automatic semaphore cleanup when idle  
✅ **Pure functional**: No blocking, all effects in F[_]  

### Best Use Cases

- **High throughput**: Many operations across different keys
- **Low contention**: Operations distributed across key space
- **Functional codebases**: Integrates with Cats Effect ecosystem

## Troubleshooting

### Out of Memory

If benchmarks fail with OOM:

```bash
# Edit run-benchmarks.sh or quick-benchmark.sh
# Add JVM args in sbt command:
-jvmArgs "-Xmx2g -Xms2g"
```

### Inconsistent Results

- Close other applications
- Run with more forks: `-f 3`
- Increase iterations: `-i 20 -wi 10`
- Ensure machine is not throttling (cooling, power settings)

### Benchmark Takes Too Long

Use quick benchmark for development:

```bash
./quick-benchmark.sh
```

Or run only specific implementation:

```bash
sbt "project benchmark" "Jmh/run SequentiallyCatsBenchmark -i 5 -wi 2 -f 1"
```

## Advanced Benchmarking

### Compare Different Key Distributions

Create new benchmarks for specific scenarios:

```scala
// High contention (few keys)
@Benchmark
def applyHighContention(): Unit = {
  val futures = (1 to 100).map(i => sequentially(i % 2) {})
  futures.foreach(f => Await.result(f, 10.seconds))
}

// Low contention (many keys)
@Benchmark
def applyLowContention(): Unit = {
  val futures = (1 to 100).map(i => sequentially(i) {})
  futures.foreach(f => Await.result(f, 10.seconds))
}
```

### Profiling

Run with async profiler for detailed analysis:

```bash
sbt "project benchmark" "Jmh/run -prof async:output=flamegraph SequentiallyCatsBenchmark"
```

## Contributing Benchmark Results

When sharing benchmark results, include:

1. Hardware specs (CPU, RAM)
2. JVM version (`java -version`)
3. Scala version
4. Full benchmark output or JSON file
5. Any custom configurations

## Resources

- [JMH Documentation](https://openjdk.java.net/projects/code-tools/jmh/)
- [sbt-jmh Plugin](https://github.com/sbt/sbt-jmh)
- [JMH Best Practices](https://shipilev.net/talks/jmh-oredev-nov2017.pdf)

