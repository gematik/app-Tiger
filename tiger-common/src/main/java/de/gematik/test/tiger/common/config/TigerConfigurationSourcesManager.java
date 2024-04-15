/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.config;

import static java.util.Comparator.reverseOrder;

import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Stream;

/**
 * Capsules the loaded sources in a thread-safe manner. Due to the sources implementing Comparable
 * the list is always in order.
 */
public class TigerConfigurationSourcesManager {

  private ConcurrentSkipListSet<AbstractTigerConfigurationSource> loadedSources =
      new ConcurrentSkipListSet<>();

  public void reset() {
    loadedSources.clear();
  }

  /** Get a list that has the most important value at the first position (for findFirst() calls) */
  public Stream<AbstractTigerConfigurationSource> getSortedStream() {
    return loadedSources.stream();
  }

  /**
   * Get a list that has the most important value at the last position (for looping and replacing)
   */
  public List<AbstractTigerConfigurationSource> getSortedListReversed() {
    return loadedSources.stream().sorted(reverseOrder()).toList();
  }

  public void addNewSource(AbstractTigerConfigurationSource source) {
    boolean success = loadedSources.add(source);
    if (!success) {
      final AbstractTigerConfigurationSource exisitingSource =
          loadedSources.stream()
              .filter(src -> src.getSourceType() == source.getSourceType())
              .findFirst()
              .orElseThrow();
      exisitingSource.putAll(source);
    }
  }

  public boolean removeSource(AbstractTigerConfigurationSource source) {
    return loadedSources.remove(source);
  }
}
