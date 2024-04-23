/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
