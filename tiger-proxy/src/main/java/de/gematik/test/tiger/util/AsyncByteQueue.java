package de.gematik.test.tiger.util;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelContent;
import de.gematik.test.tiger.proxy.data.TcpConnectionEntry;
import de.gematik.test.tiger.proxy.data.TcpIpConnectionIdentifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
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
    volatile int readPos = 0; // Tracks the consumed position
    Node previous, next;
    Consumer<RbelElement> preProcessingMessageManipulator;

    Node(TcpConnectionEntry value) {
      this.uuid = value.getUuid();
      this.data = value.getData();
      this.isPrimaryDirection = value.getConnectionIdentifier().isSameDirectionAs(primaryDirection);
      this.additionalData = value.getAdditionalData();
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

  public TcpConnectionEntry write(TcpConnectionEntry value) {
    Node newNode = new Node(value);
    Node prevTail = tail.getAndSet(newNode);
    if (prevTail == null) {
      head.set(newNode);
    } else {
      prevTail.next = newNode;
      newNode.previous = prevTail;
    }
    var previousUuid = lastBufferedUuid.getAndSet(value.getUuid());
    if (value.getPreviousUuid() != null) {
      previousUuid = value.getPreviousUuid();
    }
    return value.toBuilder().previousUuid(previousUuid).build();
  }

  public synchronized TcpConnectionEntry peek() {
    if (head.get() == null) {
      return TcpConnectionEntry.empty();
    }
    RbelContent data = RbelContent.builder().build();
    Node current = head.get();
    boolean initialDirection = current.isPrimaryDirection;
    var uuid = current.uuid;
    val initialReadPos = current.readPos;
    val sourceUuids = new ArrayList<String>();

    do {
      if (current.isPrimaryDirection == initialDirection) {
        int availableBytes = current.availableBytes();
        if (availableBytes > 0) {
          RbelContent snapshot;
          if (current.readPos > 0) {
            snapshot =
                RbelContent.of(
                    current.data.subArray(current.readPos, current.readPos + availableBytes));
          } else {
            snapshot = current.data;
          }
          data.append(snapshot);
          sourceUuids.add(current.uuid);
        }
      }
      current = current.next;
    } while (current != null);

    val direction = initialDirection ? primaryDirection : primaryDirection.reverse();
    return TcpConnectionEntry.builder()
        .uuid(head.get().uuid)
        .data(data)
        .connectionIdentifier(direction)
        .messagePreProcessor(head.get().preProcessingMessageManipulator)
        .previousUuid(Objects.equals(uuid, head.get().uuid) ? null : uuid)
        .positionInBaseNode(initialReadPos)
        .build()
        .addAdditionalData(head.get().additionalData)
        .addSourceUuids(sourceUuids);
  }

  public synchronized void consume(long count) {
    if (head.get() == null) {
      return;
    }
    boolean direction = head.get().isPrimaryDirection;
    var position = head.get();
    String headUuid = head.get().uuid;

    do {
      if (position.isPrimaryDirection == direction) {
        int available = position.availableBytes();
        if (count >= available) {
          // Consume entire node
          count -= available;
          removeNode(position);
        } else {
          // Partially consume the node
          position.readPos += (int) count;
          count = 0;
        }
      }
      position = position.next;
    } while (count > 0 && position != null);
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
    int total = 0;
    Node current = head.get();

    while (current != null) {
      if (current.isPrimaryDirection == head.get().isPrimaryDirection) {
        total += current.availableBytes();
      }
      current = current.next;
    }

    return total;
  }
}
