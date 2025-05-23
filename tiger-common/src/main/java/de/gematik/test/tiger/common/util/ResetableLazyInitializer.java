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
package de.gematik.test.tiger.common.util;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.concurrent.ConcurrentInitializer;
import org.apache.commons.lang3.concurrent.LazyInitializer;

/**
 * Basically the same as {@link org.apache.commons.lang3.concurrent.LazyInitializer}, but with a
 * reset method.
 */
@RequiredArgsConstructor
public class ResetableLazyInitializer<T> implements ConcurrentInitializer<T> {

  private static final Object NO_INIT = new Object();
  private final Supplier<T> supplier;

  @SuppressWarnings("unchecked")
  // Stores the managed object.
  private volatile T object = (T) NO_INIT; // NOSONAR - thread safety is ensured by the get() method

  /**
   * Returns the object wrapped by this instance. On first access the object is created. After that
   * it is cached and can be accessed pretty fast.
   *
   * @return the object initialized by this {@link LazyInitializer}
   */
  @Override
  public T get() {
    // use a temporary variable to reduce the number of reads of the
    // volatile field
    T result = object;

    if (result == NO_INIT) {
      synchronized (this) {
        result = object;
        if (result == NO_INIT) {
          object = result = initialize();
        }
      }
    }

    return result;
  }

  @SuppressWarnings("unchecked")
  public void reset() {
    T result = object;
    if (result != NO_INIT) {
      synchronized (this) {
        object = (T) NO_INIT;
      }
    }
  }

  /**
   * Creates and initializes the object managed by this {@code LazyInitializer}. This method is
   * called by {@link #get()} when the object is accessed for the first time. An implementation can
   * focus on the creation of the object. No synchronization is needed, as this is already handled
   * by {@code get()}.
   *
   * @return the managed data object
   */
  protected T initialize() {
    return supplier.get();
  }
}
