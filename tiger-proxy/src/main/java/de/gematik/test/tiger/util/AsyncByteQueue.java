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
package de.gematik.test.tiger.util;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageKind;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.common.util.TcpIpConnectionIdentifier;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class AsyncByteQueue {
  private class Node {
    final String uuid;
    final RbelContent data;
    final boolean isPrimaryDirection;
    final Map<String, Object> additionalData;
    final RbelMessageKind messageKind;
    volatile int readPos = 0; // Tracks the consumed position
    Node previous, next;
    Consumer<RbelElement> preProcessingMessageManipulator;

    Node(TcpConnectionEntry value) {
      this.uuid = value.getUuid();
      this.data = value.getData();
      this.isPrimaryDirection = value.getConnectionIdentifier().isSameDirectionAs(primaryDirection);
      this.additionalData = value.getAdditionalData();
      this.messageKind = value.getMessageKind();
      preProcessingMessageManipulator = value.getMessagePreProcessor();
    }

    public int availableBytes() {
      return data.size() - readPos;
    }
  }

  private final AtomicReference<Node> head = new AtomicReference<>(null);
  private final AtomicReference<Node> tail = new AtomicReference<>(null);
  private final TcpIpConnectionIdentifier primaryDirection;
  private final AtomicReference<String> lastBufferedUuid = new AtomicReference<>(null);
  // Track per-direction availability to avoid O(n) scans in availableBytes().
  private long availableBytesPrimary = 0;
  private long availableBytesSecondary = 0;

  private void adjustAvailableBytes(Node node, long delta) {
    if (node.isPrimaryDirection) {
      availableBytesPrimary += delta;
    } else {
      availableBytesSecondary += delta;
    }
  }

  public synchronized TcpConnectionEntry write(TcpConnectionEntry value) {
    Node newNode = new Node(value);
    Node prevTail = tail.getAndSet(newNode);
    if (prevTail == null) {
      head.set(newNode);
    } else {
      prevTail.next = newNode;
      newNode.previous = prevTail;
    }
    adjustAvailableBytes(newNode, newNode.availableBytes());
    var previousUuid = lastBufferedUuid.getAndSet(value.getUuid());
    if (value.getPreviousUuid() != null) {
      previousUuid = value.getPreviousUuid();
    }
    return value.toBuilder().previousUuid(previousUuid).build();
  }

  public synchronized TcpConnectionEntry peek() {
    var headNode = head.get();
    if (headNode == null) {
      return TcpConnectionEntry.empty();
    }
    var data = new LinkedList<RbelContent>();
    boolean initialDirection = headNode.isPrimaryDirection;
    val initialReadPos = headNode.readPos;
    val sourceUuids = new ArrayList<String>();

    for (var current = headNode; current != null; current = current.next) {
      if (current.isPrimaryDirection == initialDirection) {
        int availableBytes = current.availableBytes();
        if (availableBytes > 0) {
          RbelContent snapshot;
          if (current.readPos > 0) {
            snapshot = current.data.subArray(current.readPos, current.readPos + availableBytes);
          } else {
            snapshot = current.data;
          }
          data.add(snapshot);
          sourceUuids.add(current.uuid);
        }
      }
    }

    val direction = initialDirection ? primaryDirection : primaryDirection.reverse();
    return TcpConnectionEntry.builder()
        .uuid(headNode.uuid)
        .data(RbelContent.of(data))
        .connectionIdentifier(direction)
        .messagePreProcessor(headNode.preProcessingMessageManipulator)
        .positionInBaseNode(initialReadPos)
        .messageKind(headNode.messageKind)
        .build()
        .addAdditionalData(headNode.additionalData)
        .addSourceUuids(sourceUuids);
  }

  public synchronized void consume(long count) {
    var headNode = head.get();
    for (var currentNode = headNode;
        count > 0 && currentNode != null;
        currentNode = currentNode.next) {
      if (currentNode.isPrimaryDirection == headNode.isPrimaryDirection) {
        int available = currentNode.availableBytes();
        if (count >= available) {
          // Consume entire node
          count -= available;
          adjustAvailableBytes(currentNode, -available);
          removeNode(currentNode);
        } else {
          // Partially consume the node
          adjustAvailableBytes(currentNode, -count);
          currentNode.readPos += (int) count;
          count = 0;
        }
      }
    }
  }

  private synchronized void removeNode(Node node) {
    if (node.previous == null) { // do we need to move the head?
      head.set(node.next);
      if (node.next != null) {
        node.next.previous = null;
      } else { // at head and tail
        tail.set(null);
      }
    } else { // not at the head
      node.previous.next = node.next;
      if (node.next != null) { // not at the tail
        node.next.previous = node.previous;
      } else {
        tail.set(node.previous);
      }
    }
  }

  public synchronized boolean isEmpty() {
    return head.get() == null || availableBytes() == 0;
  }

  public synchronized int availableBytes() {
    Node headNode = head.get();
    if (headNode == null) {
      return 0;
    }
    long total =
        headNode.isPrimaryDirection ? availableBytesPrimary : availableBytesSecondary;
    return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
  }
}
