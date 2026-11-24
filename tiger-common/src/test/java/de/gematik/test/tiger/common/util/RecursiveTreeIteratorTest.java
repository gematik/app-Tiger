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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecursiveTreeIteratorTest {

  private record Node(String name, List<Node> children) {
    static Node of(String name, Node... children) {
      return new Node(name, List.of(children));
    }
  }

  /** In pre-order: a, b, d, e, c, f. */
  private static Node tree() {
    return Node.of("a", Node.of("b", Node.of("d"), Node.of("e")), Node.of("c", Node.of("f")));
  }

  @Test
  void theIteratorWalksInPreOrderAndOnlyAsFarAsItIsAsked() {
    var asked = new ArrayList<String>();
    var iterator =
        new RecursiveTreeIterator<Node>(
            tree(),
            node -> {
              asked.add(node.name());
              return node.children().iterator();
            });

    assertThat(iterator.next().name()).isEqualTo("a");
    assertThat(iterator.next().name()).isEqualTo("b");
    assertThat(asked).containsExactly("a", "b");

    var rest = new ArrayList<String>();
    iterator.forEachRemaining(node -> rest.add(node.name()));
    assertThat(rest).containsExactly("d", "e", "c", "f");
  }

  @Test
  void theEagerWalkVisitsInTheOrderOfTheIteratorAndAsksForTheChildrenOfEachNodeOnce() {
    var visited = new ArrayList<String>();
    var asked = new ArrayList<String>();

    RecursiveTreeIterator.forEachInPreOrder(
        tree(),
        (node, addChild) -> {
          asked.add(node.name());
          node.children().forEach(addChild);
        },
        node -> visited.add(node.name()));

    assertThat(visited).containsExactly("a", "b", "d", "e", "c", "f");
    assertThat(asked).containsExactly("a", "b", "d", "e", "c", "f");
  }

  @Test
  void theEagerWalkLeavesOutTheSubtreesThatNoChildIsAddedFor() {
    var visited = new ArrayList<String>();

    RecursiveTreeIterator.forEachInPreOrder(
        tree(),
        (node, addChild) -> {
          if (!node.name().equals("b")) {
            node.children().forEach(addChild);
          }
        },
        node -> visited.add(node.name()));

    assertThat(visited).containsExactly("a", "b", "c", "f");
  }

  @Test
  void theEagerWalkDoesNotUseTheCallStack() {
    int depth = 200_000;
    var count = new int[1];

    RecursiveTreeIterator.forEachInPreOrder(
        0,
        (number, addChild) -> {
          if (number < depth) {
            addChild.accept(number + 1);
          }
        },
        number -> count[0]++);

    assertThat(count[0]).isEqualTo(depth + 1);
  }
}
