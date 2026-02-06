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

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.NonNull;

/**
 * This class allows iterating over trees recursively in a stream-like, lazy fashion, avoiding to
 * first collect all (potentially many) nodes in a list via recursive call-trees. Instead, at any
 * point in time, it keeps a stack of iterators of the depth of the current element in the tree.
 *
 * @param <T>
 */
public class RecursiveTreeIterator<T> implements Iterator<T> {
  private final Deque<Iterator<? extends T>> stack = new LinkedList<>();
  private final Function<T, Iterator<? extends T>> getChildren;

  public RecursiveTreeIterator(
      @NonNull T root, @NonNull Function<T, Iterator<? extends T>> getChildren) {
    this(List.of(root).iterator(), getChildren);
  }

  public RecursiveTreeIterator(
      @NonNull Iterator<? extends T> roots,
      @NonNull Function<T, Iterator<? extends T>> getChildren) {
    if (roots.hasNext()) {
      stack.push(roots);
    }
    this.getChildren = getChildren;
  }

  @Override
  public boolean hasNext() {
    return !stack.isEmpty();
  }

  @Override
  public T next() {
    assert hasNext();
    var currentIterator = stack.peek();
    assert currentIterator != null && currentIterator.hasNext();
    var next = currentIterator.next();
    if (!currentIterator.hasNext()) {
      stack.pop();
    }
    var childrenIterator = getChildren.apply(next);
    if (childrenIterator.hasNext()) {
      stack.push(childrenIterator);
    }
    return next;
  }

  public Stream<T> stream() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(this, Spliterator.ORDERED), false);
  }
}
