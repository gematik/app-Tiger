/*
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */

package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.Builder;

public class RbelLazyValueFacet<T> implements RbelValueFacet<T> {

  private final Supplier<T> valueSupplier;
  private WeakReference<AtomicReference<T>> cachedValue = new WeakReference<>(null);

  @Builder
  public RbelLazyValueFacet(Supplier<T> valueSupplier) {
    this.valueSupplier = valueSupplier;
  }

  @Override
  public synchronized T getValue() {
    var value = cachedValue.get();
    if (value == null) {
      value = new AtomicReference<>(valueSupplier.get());
      cachedValue = new WeakReference<>(value);
    }
    return value.get();
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
