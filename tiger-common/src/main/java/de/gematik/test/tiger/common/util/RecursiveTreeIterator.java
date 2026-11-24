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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
  private final Deque<Iterator<? extends T>> stack = new ArrayDeque<>();
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

  /**
   * The eager mode, for callers that visit every node anyway: a depth-first walk from left to right
   * (a node, then the whole subtree of its first child, then that of its second child, and so on),
   * in the same order as the iterator, but without an iterator for each node. The depth of the tree
   * does not matter for the call stack, because the walk does not recurse.
   *
   * <p>Each node is visited, then {@code addChildren} is called once for it. It has to pass the
   * children of the node to the given consumer, in the order they are to be visited. A subtree is
   * left out by passing none of its root's children.
   */
  public static <T> void forEachInPreOrder(
      @NonNull T root,
      @NonNull BiConsumer<T, Consumer<T>> addChildren,
      @NonNull Consumer<T> visit) {
    final List<T> stack = new ArrayList<>();
    final Consumer<T> pushChild = stack::add;
    stack.add(root);
    while (!stack.isEmpty()) {
      final T node = stack.remove(stack.size() - 1);
      visit.accept(node);
      final int indexOfFirstChild = stack.size();
      addChildren.accept(node, pushChild);
      // the stack hands out its last entry first, so turning the children around keeps the walk
      // left to right
      reverseFrom(stack, indexOfFirstChild);
    }
  }

  /**
   * Turns around the entries from {@code from} to the end of the list, in place. {@code
   * Collections.reverse(list.subList(from, list.size()))} does the same, but creates a view object
   * for every node of the walk, which measurably adds to its allocation.
   */
  private static <T> void reverseFrom(List<T> list, int from) {
    for (int low = from, high = list.size() - 1; low < high; low++, high--) {
      final T lowerEntry = list.get(low);
      list.set(low, list.get(high));
      list.set(high, lowerEntry);
    }
  }
}
