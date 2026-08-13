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

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObservableQueue<T> extends ConcurrentLinkedQueue<T> {
  private final Runnable listener;

  public ObservableQueue(Runnable listener) {
    this.listener = listener;
  }

  @Override
  public boolean add(T element) {
    final boolean result = super.add(element);
    if (result) {
      listener.run();
    }
    return result;
  }

  @Override
  public boolean remove(Object element) {
    final boolean result = super.remove(element);
    if (result) {
      listener.run();
    }
    return result;
  }

  @Override
  public boolean addAll(Collection<? extends T> elements) {
    final boolean result = super.addAll(elements);
    if (result) {
      listener.run();
    }
    return result;
  }

  @Override
  public boolean removeAll(Collection<?> elements) {
    final boolean result = super.removeAll(elements);
    if (result) {
      listener.run();
    }
    return result;
  }

  @Override
  public boolean retainAll(Collection<?> elements) {
    final boolean result = super.retainAll(elements);
    if (result) {
      listener.run();
    }
    return result;
  }

  @Override
  public void clear() {
    if (!isEmpty()) {
      super.clear();
      listener.run();
    }
  }

  @Override
  public T poll() {
    final T result = super.poll();
    if (result != null) {
      listener.run();
    }
    return result;
  }
}
