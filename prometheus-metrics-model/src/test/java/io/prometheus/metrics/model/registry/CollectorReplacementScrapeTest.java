package io.prometheus.metrics.model.registry;

import static org.assertj.core.api.Assertions.assertThat;

import io.prometheus.metrics.model.snapshots.GaugeSnapshot;
import io.prometheus.metrics.model.snapshots.MetricSnapshots;
import org.junit.jupiter.api.Test;

class CollectorReplacementScrapeTest {

  @Test
  void scrapeUsesCollectorMembershipFromStartOfIteration() {
    assertSnapshotIteration(false);
  }

  @Test
  void filteredScrapeUsesCollectorMembershipFromStartOfIteration() {
    assertSnapshotIteration(true);
  }

  private void assertSnapshotIteration(boolean filtered) {
    PrometheusRegistry registry = new PrometheusRegistry();
    FixedHashMultiCollector old = new FixedHashMultiCollector("old", 8, () -> {});
    FixedHashMultiCollector replacement = new FixedHashMultiCollector("replacement", 12, () -> {});
    FixedHashMultiCollector first =
        new FixedHashMultiCollector(
            "first",
            0,
            () -> {
              registry.unregister(old);
              registry.register(replacement);
            });
    registry.register(first);
    registry.register(old);

    // Hashes make the former ConcurrentHashMap implementation visit the mutating
    // collector first, retain the old collector in its iterator, then see its replacement.
    MetricSnapshots snapshots = filtered ? registry.scrape(name -> true) : registry.scrape();

    assertThat(snapshots)
        .extracting(snapshot -> snapshot.getMetadata().getName())
        .containsExactlyInAnyOrder("first", "old");
  }

  private static final class FixedHashMultiCollector implements MultiCollector {
    private final String name;
    private final int hash;
    private final Runnable onCollect;

    private FixedHashMultiCollector(String name, int hash, Runnable onCollect) {
      this.name = name;
      this.hash = hash;
      this.onCollect = onCollect;
    }

    @Override
    public MetricSnapshots collect() {
      onCollect.run();
      return new MetricSnapshots(GaugeSnapshot.builder().name(name).build());
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }
  }
}
