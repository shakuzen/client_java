package io.prometheus.metrics.benchmarks;

import io.prometheus.metrics.model.registry.Collector;
import io.prometheus.metrics.model.registry.MultiCollector;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Registry membership and collection costs, with one prebuilt gauge family per collector.
 * Collectors omit optional registration metadata, as legacy adapter collectors can do. Metric
 * construction and sample collection allocations are excluded so registry storage costs remain
 * visible. Each register/unregister invocation adds and removes one collector; registerAll builds a
 * new registry containing all resident collectors.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class RegistryBenchmark {

  @State(Scope.Thread)
  public static class RegistryState {

    @Param({"100", "1000", "10000"})
    public int collectorCount;

    @Param({"collector", "multiCollector"})
    public String collectorType;

    PrometheusRegistry registry;
    Collector[] collectors;
    MultiCollector[] multiCollectors;
    Collector candidate;
    MultiCollector multiCandidate;

    @Setup
    public void setup() {
      collectors = new Collector[collectorCount];
      multiCollectors = new MultiCollector[collectorCount];
      for (int i = 0; i < collectorCount; i++) {
        MetricSnapshot snapshot =
            GaugeSnapshot.builder()
                .name("resident_" + i)
                .dataPoint(GaugeSnapshot.GaugeDataPointSnapshot.builder().value(1).build())
                .build();
        MetricSnapshots snapshots = new MetricSnapshots(snapshot);
        collectors[i] = () -> snapshot;
        multiCollectors[i] = () -> snapshots;
      }
      MetricSnapshot snapshot =
          GaugeSnapshot.builder()
              .name("candidate")
              .dataPoint(GaugeSnapshot.GaugeDataPointSnapshot.builder().value(1).build())
              .build();
      MetricSnapshots snapshots = new MetricSnapshots(snapshot);
      candidate = () -> snapshot;
      multiCandidate = () -> snapshots;
      registry = newRegistry();
    }

    PrometheusRegistry newRegistry() {
      PrometheusRegistry result = new PrometheusRegistry();
      if (collectorType.equals("collector")) {
        for (Collector collector : collectors) {
          result.register(collector);
        }
      } else {
        for (MultiCollector collector : multiCollectors) {
          result.register(collector);
        }
      }
      return result;
    }
  }

  @Benchmark
  public PrometheusRegistry registerAll(RegistryState state) {
    return state.newRegistry();
  }

  @Benchmark
  public void registerUnregister(RegistryState state) {
    if (state.collectorType.equals("collector")) {
      state.registry.register(state.candidate);
      state.registry.unregister(state.candidate);
    } else {
      state.registry.register(state.multiCandidate);
      state.registry.unregister(state.multiCandidate);
    }
  }

  @Benchmark
  public MetricSnapshots scrape(RegistryState state) {
    return state.registry.scrape();
  }
}
