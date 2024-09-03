package com.evolutiongaming.concurrent.sequentially

import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(
  value = 1,
  jvmArgs = Array(
    "-server",
    "-Xms1g",
    "-Xmx1g",
    "-XX:NewSize=512m",
    "-XX:MaxNewSize=512m",
    "-XX:InitialCodeCacheSize=256m",
    "-XX:ReservedCodeCacheSize=256m",
    "-XX:-UseBiasedLocking",
    "-XX:+AlwaysPreTouch",
    "-XX:+UseParallelGC",
  ),
)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
abstract class Common {}
