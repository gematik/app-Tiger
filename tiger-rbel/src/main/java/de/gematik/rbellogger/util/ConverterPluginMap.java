/*
 *
 * Copyright 2025 gematik GmbH
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
 */
package de.gematik.rbellogger.util;

import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConverterPluginMap {

  private static final OrderedSet EMPTY_ORDERED_SET = new OrderedSet();
  private final Map<RbelConversionPhase, OrderedSet> source = new ConcurrentHashMap<>();

  public void put(RbelConverterPlugin value) {
    source.computeIfAbsent(value.getPhase(), k -> new OrderedSet()).add(value);
  }

  public void clear() {
    source.clear();
  }

  public OrderedSet get(RbelConversionPhase key) {
    return source.getOrDefault(key, EMPTY_ORDERED_SET);
  }

  public void forEach(Consumer<RbelConverterPlugin> pluginConsumer) {
    source.values().forEach(orderedSet -> orderedSet.forEach(pluginConsumer));
  }

  public Stream<RbelConverterPlugin> stream() {
    return source.values().stream().flatMap(OrderedSet::stream);
  }

  public static class OrderedSet implements Iterable<RbelConverterPlugin> {
    private final ConcurrentSkipListSet<Entry> set = new ConcurrentSkipListSet<>();
    private final AtomicLong insertionOrder = new AtomicLong(0);

    public void add(RbelConverterPlugin item) {
      set.add(new Entry(item, insertionOrder.getAndIncrement()));
    }

    public void clear() {
      set.clear();
    }

    public boolean isEmpty() {
      return set.isEmpty();
    }

    public int size() {
      return set.size();
    }

    public ConcurrentSkipListSet<Entry> getSet() {
      return set;
    }

    @Override
    public Iterator<RbelConverterPlugin> iterator() {
      return new Iterator<>() {
        private final Iterator<Entry> iterator = set.iterator();

        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public RbelConverterPlugin next() {
          return iterator.next().getValue();
        }
      };
    }

    public Stream<RbelConverterPlugin> stream() {
      return set.stream().map(Entry::getValue);
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    private static class Entry implements Comparable<Entry> {
      private final RbelConverterPlugin value;
      private final long order;

      @Override
      public int compareTo(Entry other) {
        int comparison = -comparePhasesAndPriority(other);
        if (comparison != 0) {
          return comparison;
        }
        return Long.compare(this.order, other.order);
      }

      private int comparePhasesAndPriority(Entry other) {
        int phaseComparison = this.getValue().getPhase().compareTo(other.getValue().getPhase());
        if (phaseComparison != 0) {
          return phaseComparison;
        }
        return Integer.compare(this.getValue().getPriority(), other.getValue().getPriority());
      }
    }
  }
}
