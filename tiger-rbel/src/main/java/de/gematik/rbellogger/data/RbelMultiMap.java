/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.rbellogger.data;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
public class RbelMultiMap<T> implements Map<String, T> {

    public static final Collector<Entry<String, ?>, RbelMultiMap, RbelMultiMap> COLLECTOR =
        Collector.of(RbelMultiMap::new, RbelMultiMap::put, (m1, m2) -> {
            m1.putAll(m2);
            return m1;
        });
    public static final RbelMultiMap<RbelElement> EMPTY = new RbelMultiMap<>();

    private final List<Entry<String, T>> values = new ArrayList();

    @Override
    public int size() {
        return values.size();
    }

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return values.stream()
            .anyMatch(entry -> entry.getKey().equals(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return values.stream()
            .anyMatch(entry -> entry.getValue().equals(value));
    }

    @Override
    public T get(Object key) {
        return values.stream()
            .filter(entry -> entry.getKey().equals(key))
            .map(Entry::getValue)
            .findFirst().orElse(null);
    }

    @Override
    public T put(String key, T value) {
        values.add(Pair.of(key, value));
        return null;
    }

    public T put(Entry<String, T> value) {
        values.add(value);
        return null;
    }

    @Override
    public T remove(Object key) {
        return removeAll(key.toString()).stream()
            .findFirst().orElse(null);
    }

    public List<T> removeAll(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        final Iterator<Entry<String, T>> iterator = values.iterator();
        List<T> removed = new ArrayList<>();
        while (iterator.hasNext()) {
            final Entry<String, T> entry = iterator.next();
            if (key.equals(entry.getKey())) {
                iterator.remove();
                removed.add(entry.getValue());
            }
        }
        return removed;
    }

    @Override
    public void putAll(Map m) {
        for (Object entryRaw : m.entrySet()) {
            Entry entry = (Entry) entryRaw;
            values.add(Pair.of((String) entry.getKey(), (T) entry.getValue()));
        }
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public Set<String> keySet() {
        return values.stream()
            .map(Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Deprecated
    public List<T> values() {
        return stream()
            .map(Entry::getValue)
            .collect(Collectors.toUnmodifiableList());
    }

    @Override
    @Deprecated
    public Set<Entry<String, T>> entrySet() {
        return stream()
            .collect(Collectors.toUnmodifiableSet());
    }

    public Stream<Entry<String, T>> stream() {
        return values.stream();
    }

    public RbelMultiMap with(String key, T value) {
        put(key, value);
        return this;
    }

    public RbelMultiMap withSkipIfNull(String key, T value) {
        if (value != null) {
            put(key, value);
        }
        return this;
    }

    public List<T> getAll(String key) {
        return values.stream()
            .filter(entry -> entry.getKey().equals(key))
            .map(Entry::getValue)
            .collect(Collectors.toUnmodifiableList());
    }

    public Iterator<Entry<String, T>> iterator() {
        return values.listIterator();
    }
}
