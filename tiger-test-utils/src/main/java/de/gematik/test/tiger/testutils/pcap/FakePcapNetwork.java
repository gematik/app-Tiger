/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.test.tiger.testutils.pcap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.net.Inet4Address;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapStat;
import org.pcap4j.packet.namednumber.DataLinkType;

/**
 * A network without a network card, standing in for libpcap in tests of the capture engines. An
 * engine is put on it by overriding {@code resolveInterfaces} (with {@link #resolve}) and {@code
 * openHandle} (with {@link #open}); what it reads comes from {@link #deliver}. {@link #LOOPBACK}
 * always exists.
 */
public final class FakePcapNetwork {

  /** The interface an engine captures on when it is not told which ones. */
  public static final String LOOPBACK = "fake-loopback";

  private static final long READ_TIMEOUT_MILLIS = 10;
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

  /** What an engine asked for when it opened an interface. */
  public record OpenedHandle(String interfaceName, int snaplenBytes, int bufferBytes) {}

  private record Frame(byte[] data, Timestamp capturedAt) {}

  private static final class FakeInterface {
    final String name;
    final PcapNetworkInterface networkInterface = stub(PcapNetworkInterface.class);
    final BlockingQueue<Frame> pending = new LinkedBlockingQueue<>();
    final AtomicLong delivered = new AtomicLong();
    final AtomicLong processed = new AtomicLong();
    final List<String> kernelFilters = new CopyOnWriteArrayList<>();
    final List<String> compiledFilters = new CopyOnWriteArrayList<>();
    final AtomicReference<Throwable> readFailure = new AtomicReference<>();
    volatile boolean failToOpen;
    volatile boolean failFilters;
    volatile boolean failStats;
    final List<String> refusedFragments = new CopyOnWriteArrayList<>();
    volatile long dropped;
    volatile long droppedByInterface;
    volatile AtomicBoolean currentHandleOpen = new AtomicBoolean(false);

    FakeInterface(String name) {
      this.name = name;
      when(networkInterface.getName()).thenReturn(name);
    }

    void refuseFilterIfToldTo(String expression) throws PcapNativeException {
      if (failFilters) {
        throw new PcapNativeException("fake: " + name + " refuses filters");
      }
      if (refusedFragments.stream().anyMatch(expression::contains)) {
        throw new PcapNativeException("fake: " + name + " cannot compile '" + expression + "'");
      }
    }
  }

  private final Map<String, FakeInterface> interfaces = new LinkedHashMap<>();
  private final List<OpenedHandle> opened = new CopyOnWriteArrayList<>();
  private final Map<String, Predicate<byte[]>> filterRules = new ConcurrentHashMap<>();

  /** A network with {@link #LOOPBACK} and the given further interfaces. */
  public FakePcapNetwork(String... furtherInterfaces) {
    interfaces.put(LOOPBACK, new FakeInterface(LOOPBACK));
    for (String name : furtherInterfaces) {
      interfaces.put(name, new FakeInterface(name));
    }
  }

  /**
   * The interfaces an engine asked for by name, in that order, skipping unknown names; {@link
   * #LOOPBACK} if it asked for none.
   */
  public synchronized List<PcapNetworkInterface> resolve(List<String> names) {
    if (names == null || names.isEmpty()) {
      return List.of(interfaces.get(LOOPBACK).networkInterface);
    }
    List<PcapNetworkInterface> resolved = new ArrayList<>();
    for (String name : names) {
      FakeInterface known = interfaces.get(name);
      if (known != null) {
        resolved.add(known.networkInterface);
      }
    }
    return resolved;
  }

  /** Opens {@code networkInterface} as a live handle; fails if {@link #failToOpen} said so. */
  public PcapHandle open(PcapNetworkInterface networkInterface, int snaplenBytes, int bufferBytes)
      throws PcapNativeException {
    FakeInterface fake = interfaceNamed(networkInterface.getName());
    if (fake.failToOpen) {
      throw new PcapNativeException("fake: " + fake.name + " cannot be opened");
    }
    opened.add(new OpenedHandle(fake.name, snaplenBytes, bufferBytes));
    try {
      return newHandle(fake, snaplenBytes);
    } catch (PcapNativeException | NotOpenException e) {
      throw new IllegalStateException("Stubbing a fake handle cannot fail", e);
    }
  }

  private PcapHandle newHandle(FakeInterface fake, int snaplenBytes)
      throws PcapNativeException, NotOpenException {
    PcapHandle handle = stub(PcapHandle.class);
    AtomicBoolean open = new AtomicBoolean(true);
    AtomicBoolean frameInHand = new AtomicBoolean(false);
    AtomicReference<Timestamp> lastCapturedAt = new AtomicReference<>(new Timestamp(0));
    fake.currentHandleOpen = open;

    when(handle.isOpen()).thenAnswer(call -> open.get());
    when(handle.getDlt()).thenReturn(DataLinkType.EN10MB);
    when(handle.getSnapshot()).thenReturn(snaplenBytes);
    when(handle.getTimestamp()).thenAnswer(call -> lastCapturedAt.get());
    when(handle.getNextRawPacket())
        .thenAnswer(
            call -> {
              if (frameInHand.getAndSet(false)) {
                fake.processed.incrementAndGet(); // the engine is back: it is done with the last
              }
              Throwable failure = fake.readFailure.getAndSet(null);
              if (failure != null) {
                throw failure;
              }
              Frame frame = fake.pending.poll(READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
              if (frame == null) {
                return null;
              }
              lastCapturedAt.set(frame.capturedAt());
              frameInHand.set(true);
              return frame.data();
            });
    doAnswer(
            call -> {
              open.set(false);
              return null;
            })
        .when(handle)
        .close();
    doAnswer(
            call -> {
              fake.refuseFilterIfToldTo(call.getArgument(0));
              fake.kernelFilters.add(call.getArgument(0));
              return null;
            })
        .when(handle)
        .setFilter(anyString(), any(BpfCompileMode.class));
    when(handle.compileFilter(anyString(), any(BpfCompileMode.class), any(Inet4Address.class)))
        .thenAnswer(
            call -> {
              String expression = call.getArgument(0);
              fake.refuseFilterIfToldTo(expression);
              fake.compiledFilters.add(expression);
              BpfProgram program = stub(BpfProgram.class);
              when(program.applyFilter(any(byte[].class)))
                  .thenAnswer(
                      apply ->
                          filterRules
                              .getOrDefault(expression, frame -> true)
                              .test(apply.getArgument(0)));
              return program;
            });
    when(handle.getStats())
        .thenAnswer(
            call -> {
              if (fake.failStats) {
                throw new PcapNativeException("fake: no statistics on " + fake.name);
              }
              PcapStat stats = stub(PcapStat.class);
              when(stats.getNumPacketsDropped()).thenReturn(fake.dropped);
              when(stats.getNumPacketsDroppedByIf()).thenReturn(fake.droppedByInterface);
              when(stats.getNumPacketsReceived()).thenReturn(fake.delivered.get());
              return stats;
            });
    return handle;
  }

  /** A mock that only answers: recording every call of a reader that polls all day is waste. */
  private static <T> T stub(Class<T> type) {
    return mock(type, withSettings().stubOnly());
  }

  private FakeInterface interfaceNamed(String name) {
    synchronized (this) {
      FakeInterface fake = interfaces.get(name);
      if (fake == null) {
        throw new IllegalArgumentException("No such fake interface: " + name);
      }
      return fake;
    }
  }

  /** Makes every later attempt to open {@code interfaceName} fail, as without permission. */
  public void failToOpen(String interfaceName) {
    interfaceNamed(interfaceName).failToOpen = true;
  }

  /** Makes every later change of a filter on {@code interfaceName}'s handles fail. */
  public void failFilters(String interfaceName) {
    interfaceNamed(interfaceName).failFilters = true;
  }

  /**
   * Makes every later filter on {@code interfaceName} that contains {@code fragment} fail, as an
   * invalid expression does; other filters keep working.
   */
  public void refuseFiltersContaining(String interfaceName, String fragment) {
    interfaceNamed(interfaceName).refusedFragments.add(fragment);
  }

  /** Makes every later request for the statistics of {@code interfaceName}'s handles fail. */
  public void failStats(String interfaceName) {
    interfaceNamed(interfaceName).failStats = true;
  }

  /** Lets the next read on {@code interfaceName} fail with {@code failure}, once. */
  public void failNextRead(String interfaceName, Throwable failure) {
    interfaceNamed(interfaceName).readFailure.set(failure);
  }

  /** Whether a failure set by {@link #failNextRead} is still waiting for the engine's next read. */
  public boolean hasPendingReadFailure(String interfaceName) {
    return interfaceNamed(interfaceName).readFailure.get() != null;
  }

  /** What an engine's software filter with the given BPF expression accepts; all by default. */
  public void filterAccepts(String bpfExpression, Predicate<byte[]> accepts) {
    filterRules.put(bpfExpression, accepts);
  }

  /** Reports packet drops on {@code interfaceName} from now on, as a busy kernel buffer does. */
  public void dropPackets(String interfaceName, long byBuffer, long byInterface) {
    FakeInterface fake = interfaceNamed(interfaceName);
    fake.dropped = byBuffer;
    fake.droppedByInterface = byInterface;
  }

  /** Puts a frame on the wire of {@code interfaceName}, captured now. */
  public void deliver(String interfaceName, byte[] frame) {
    deliver(interfaceName, frame, Instant.now());
  }

  /** Puts a frame on the wire of {@code interfaceName}, captured at {@code capturedAt}. */
  public void deliver(String interfaceName, byte[] frame, Instant capturedAt) {
    FakeInterface fake = interfaceNamed(interfaceName);
    fake.delivered.incrementAndGet();
    fake.pending.add(new Frame(frame, Timestamp.from(capturedAt)));
  }

  /** Waits until the engine has read every frame delivered to the interface so far. */
  public void awaitProcessed(String interfaceName) {
    FakeInterface fake = interfaceNamed(interfaceName);
    long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
    while (fake.processed.get() < fake.delivered.get()) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException(
            "Frames on "
                + interfaceName
                + " not taken in: "
                + fake.processed.get()
                + " of "
                + fake.delivered.get());
      }
      try {
        Thread.sleep(2);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for " + interfaceName, e);
      }
    }
  }

  /** Every interface an engine opened so far, in order, with what it asked for. */
  public List<OpenedHandle> opened() {
    return List.copyOf(opened);
  }

  /** The BPF expressions set as kernel filter on {@code interfaceName}, in order. */
  public List<String> kernelFilters(String interfaceName) {
    return List.copyOf(interfaceNamed(interfaceName).kernelFilters);
  }

  /** The BPF expressions compiled for software filtering on {@code interfaceName}, in order. */
  public List<String> compiledFilters(String interfaceName) {
    return List.copyOf(interfaceNamed(interfaceName).compiledFilters);
  }

  /** Whether the engine's handle on {@code interfaceName} is open: opened and not yet closed. */
  public boolean isOpen(String interfaceName) {
    return interfaceNamed(interfaceName).currentHandleOpen.get();
  }
}
