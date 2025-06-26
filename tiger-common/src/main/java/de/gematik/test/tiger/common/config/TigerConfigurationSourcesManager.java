/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
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

  private ConcurrentSkipListSet<TigerConfigurationSource> loadedSources =
      new ConcurrentSkipListSet<>();

  public void reset() {
    loadedSources.clear();
  }

  /** Get a list that has the most important value at the first position (for findFirst() calls) */
  public Stream<TigerConfigurationSource> getSortedStream() {
    return loadedSources.stream();
  }

  /**
   * Get a list that has the most important value at the last position (for looping and replacing)
   */
  public List<TigerConfigurationSource> getSortedListReversed() {
    return loadedSources.stream().sorted(reverseOrder()).toList();
  }

  public void addNewSource(TigerConfigurationSource source) {
    boolean success = loadedSources.add(source);
    if (!success) {
      final TigerConfigurationSource exisitingSource =
          loadedSources.stream()
              .filter(src -> src.getPrecedence() == source.getPrecedence())
              .findFirst()
              .orElseThrow();
      exisitingSource.putAll(source);
    }
  }

  public boolean removeSource(TigerConfigurationSource source) {
    return loadedSources.remove(source);
  }
}
