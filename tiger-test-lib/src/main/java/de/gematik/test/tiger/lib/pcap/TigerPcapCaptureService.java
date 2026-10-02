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
package de.gematik.test.tiger.lib.pcap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapStat;
import org.pcap4j.packet.Packet;

/**
 * Owns the pcap capture lifecycle: opening interfaces, applying BPF filters, managing packet
 * capture, and rotating per-testcase dumpers.
 *
 * <p>Features (v1, local capture):
 *
 * <ul>
 *   <li>One continuous capture thread + packet-at-a-time processing (no buffer accumulation).
 *   <li>Per-testcase {@code PcapDumper} rotation on scenario boundaries.
 *   <li>BPF filter narrowed to runtime ports of all local {@code TigerProxyServer} instances.
 *   <li>Graceful fallback when native lib / capability is missing (WARN, no test failure).
 * </ul>
 *
 * <p>Thread safety: Packet I/O happens on a dedicated reader thread; {@code currentDumper} is
 * volatile and swapped from test reporter callback threads via {@link #rotate(Path)}.
 *
 * <p>See {@code doc/adr/020_pcap_capture.md}.
 */
@Slf4j
public class TigerPcapCaptureService {

  @Setter @Getter private static TigerPcapCaptureService instance;

  @Getter private boolean enabled = false;
  @Setter private List<String> interfaceNames;
  @Setter private String manualBpfFilter;
  private PcapHandle pcapHandle;
  private Thread readerThread;
  private PcapPacketDumper currentDumper;
  private volatile boolean shouldStop = false;
  @Setter private Set<Integer> lastStartPorts;

  private volatile boolean needsLazyDumper = false;

  private final int snaplenKb;
  private final int bufferSizeKb;
  private static final int TIMEOUT_MS = 10;

  /**
   * Creates a capture service with configuration parameters.
   *
   * @param snaplenKb per-packet bytes copied to userspace, in KB (1 KB = 1024 B).
   * @param bufferSizeKb kernel ring buffer size, in KB.
   */
  public TigerPcapCaptureService(int snaplenKb, int bufferSizeKb) {
    this.snaplenKb = snaplenKb;
    this.bufferSizeKb = bufferSizeKb;
  }

  /** Exposes the tracked port set for assertions. Extracted for testability. */
  Set<Integer> getLastStartPortsForTesting() {
    return lastStartPorts;
  }

  /**
   * Widens the live BPF filter to also cover {@code newPorts}, on top of whatever the filter
   * already captures. Never narrows.
   *
   * <p>A no-op if capture is not running, if a manual filter is configured (the operator's explicit
   * filter is never overridden), if {@code newPorts} adds nothing new, or if capture already
   * started unrestricted (no ports = no filter = everything already captured — applying a port
   * filter now would narrow it, not widen it).
   *
   * @param newPorts additional TCP ports to capture, e.g. from a server started mid-run.
   */
  public synchronized void alsoCapturePorts(Set<Integer> newPorts) {
    if (!enabled) {
      return;
    }
    // Already unrestricted (no filter); applying one now would narrow, not widen.
    if (lastStartPorts == null || lastStartPorts.isEmpty()) {
      return;
    }
    if (manualBpfFilter != null && !manualBpfFilter.isBlank()) {
      return;
    }

    Optional<Set<Integer>> widened = computeWidenedPorts(lastStartPorts, newPorts);
    if (widened.isEmpty()) {
      return;
    }

    try {
      String bpfFilter = resolveBpfFilter(widened.get());
      if (!bpfFilter.isEmpty()) {
        pcapHandle.setFilter(bpfFilter, BpfCompileMode.OPTIMIZE);
        log.debug("Widened pcap BPF filter to: {}", bpfFilter);
      }
      lastStartPorts = widened.get();
    } catch (PcapNativeException | NotOpenException e) {
      log.warn("Failed to widen pcap BPF filter: {}", e.getMessage());
      log.debug("Filter widening error details:", e);
    }
  }

  /**
   * Pure computation of the merged port set for {@link #alsoCapturePorts}: {@code currentPorts}
   * plus {@code newPorts}, or empty if {@code newPorts} is null/empty or adds nothing not already
   * covered.
   */
  static Optional<Set<Integer>> computeWidenedPorts(
      Set<Integer> currentPorts, Set<Integer> newPorts) {
    if (newPorts == null || newPorts.isEmpty()) {
      return Optional.empty();
    }
    Set<Integer> merged = new TreeSet<>(currentPorts == null ? Set.of() : currentPorts);
    boolean changed = merged.addAll(newPorts);
    return changed ? Optional.of(merged) : Optional.empty();
  }

  /**
   * Starts in-process packet capture on the local loopback interface, filtered to the given TCP
   * ports. Spawns a reader thread that captures packets continuously until {@link #stop()} is
   * called.
   *
   * <p>On any failure (missing native lib, permission denied, etc.), logs a WARN once and leaves
   * {@code enabled=false}. Subsequent {@link #rotate(Path)} / {@link #stop()} calls become no-ops.
   * No exception is propagated.
   *
   * @param ports set of TCP ports to capture on (e.g. proxy ports + admin ports). Empty set yields
   *     unrestricted capture; callers should not pass empty unless intentional.
   */
  public void start(Set<Integer> ports) {
    try {
      if (enabled) {
        log.trace("Capture already started; skipping");
        return;
      }

      lastStartPorts = ports;
      log.debug(
          "Starting pcap capture service with ports: {} and interfaces: {}", ports, interfaceNames);

      Optional<PcapNetworkInterface> interfaceOpt = resolveCaptureInterface();
      if (interfaceOpt.isEmpty()) {
        String msg =
            interfaceNames == null || interfaceNames.isEmpty()
                ? "No loopback interface found; pcap capture requires one"
                : "Interfaces " + interfaceNames + " not found or no interfaces available";
        throw new PcapNativeException(msg);
      }

      PcapNetworkInterface iface = interfaceOpt.get();
      log.atDebug()
          .addArgument(iface::getName)
          .log(
              "Opening pcap capture on interface '{}' with snaplen={} KB, buffer={} KB",
              snaplenKb,
              bufferSizeKb);

      // Convert KB to bytes, with clamping at native libpcap max (65535 B).
      int snaplenBytes = Math.min(snaplenKb * 1024, 65535);
      if (snaplenBytes < snaplenKb * 1024) {
        log.warn(
            "Snaplen requested={} KB ({} B) exceeds native max 65535 B; clamping to 65535 B",
            snaplenKb,
            snaplenKb * 1024);
      }

      pcapHandle =
          iface.openLive(
              snaplenBytes, PcapNetworkInterface.PromiscuousMode.NONPROMISCUOUS, TIMEOUT_MS);
      log.debug("Pcap handle opened successfully");

      String bpfFilter = resolveBpfFilter(ports);
      if (!bpfFilter.isEmpty()) {
        pcapHandle.setFilter(bpfFilter, BpfCompileMode.OPTIMIZE);
        log.debug("BPF filter applied: {}", bpfFilter);
      } else {
        log.debug("No BPF filter applied (empty port set or no valid ports)");
      }

      enabled = true;
      shouldStop = false;

      // Start reader thread that pulls packets and routes them to the current dumper.
      readerThread = new Thread(this::captureLoop, "TigerPcapReader");
      readerThread.setDaemon(true);
      readerThread.start();

      log.atDebug()
          .addArgument(readerThread::getName)
          .log("Pcap capture started successfully on thread '{}'");
    } catch (PcapNativeException
        | UnsatisfiedLinkError
        | NoClassDefFoundError
        | SecurityException e) {
      handleStartupFailure("Native pcap library unavailable or permission denied", e);
    } catch (RuntimeException | NotOpenException e) {
      handleStartupFailure("Unexpected error during pcap startup", e);
    }
  }

  /**
   * Resolves the effective BPF filter for this start(): the manual filter if configured
   * (non-blank), otherwise the union of the given ports.
   */
  String resolveBpfFilter(Set<Integer> ports) {
    return (manualBpfFilter != null && !manualBpfFilter.isBlank())
        ? manualBpfFilter
        : BpfFilterBuilder.forTcpPorts(ports);
  }

  /** Resolves the capture interface. Extracted for testability (simulating no native lib). */
  protected Optional<PcapNetworkInterface> resolveCaptureInterface() throws PcapNativeException {
    return PcapNetworkInterfaceResolver.resolve(interfaceNames);
  }

  /**
   * Handles startup failures gracefully by logging and disabling capture.
   *
   * @param reason descriptive failure reason.
   * @param e the exception that occurred.
   */
  private void handleStartupFailure(String reason, Throwable e) {
    log.warn("Pcap capture startup failed ({}); disabling capture: {}", reason, e.getMessage());
    log.debug("Pcap startup error details:", e);
    enabled = false;
    if (pcapHandle != null) {
      try {
        pcapHandle.close();
      } catch (RuntimeException closeEx) {
        log.debug("Error closing pcap handle after startup failure", closeEx);
      }
      pcapHandle = null;
    }
  }

  /**
   * Atomically swaps the current {@code PcapDumper} to a new file. The old dumper (if any) is
   * flushed and closed; the new one is created and opened immediately.
   *
   * <p>Captures any in-flight packets into the old dumper before the swap. Typically called at
   * scenario boundaries when {@code splitByTestcase=true}.
   *
   * <p>If capture is not active, this is a no-op.
   *
   * @param targetFile destination file path for the new `.pcapng` file.
   */
  public synchronized void rotate(Path targetFile) {
    if (!enabled) {
      log.trace("Capture not active; skipping rotate() to {}", targetFile);
      return;
    }

    try {
      logDropsIfAny();
      openNewAndCloseCurrentDumper(targetFile);
      log.debug("Rotated pcap dumper to {}", targetFile);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to rotate pcap dumper to {}; disabling capture: {}", targetFile, e.getMessage());
      log.debug("Rotation error details:", e);
      enabled = false;
    }
  }

  private synchronized void openNewAndCloseCurrentDumper(Path targetFile) {
    PcapPacketDumper previousDumper = currentDumper;
    PcapPacketDumper newDumper = new PcapPacketDumper(targetFile);
    try {
      newDumper.open(pcapHandle);
    } catch (Exception openEx) {
      if (previousDumper != null) {
        previousDumper.close();
      }
      currentDumper = null;
      throw new RuntimeException(openEx);
    }
    currentDumper = newDumper;
    if (previousDumper != null) {
      previousDumper.close();
    }
  }

  /**
   * Number of packets written to the currently open dumper, or {@code 0} if none is open. Read this
   * before {@link #rotate(Path)} or {@link #stop()} to know whether the file about to be closed
   * actually captured anything — a pcapng file has a non-zero section-header block even with zero
   * packets, so {@code Files.size() > 0} alone cannot tell.
   */
  public synchronized long getCurrentDumperPacketCount() {
    return currentDumper == null ? 0 : currentDumper.getPacketCount();
  }

  /**
   * Stops capture, closes the pcap handle, and closes the current dumper if open.
   *
   * <p>Blocks until the reader thread terminates.
   *
   * <p>If capture is not running, this is a no-op.
   */
  public synchronized void stop() {
    if (!enabled) {
      log.trace("Capture not active; skipping stop()");
      return;
    }

    try {
      shouldStop = true;
      waitForReader();

      if (currentDumper != null) {
        currentDumper.close();
        currentDumper = null;
      }

      if (pcapHandle != null && pcapHandle.isOpen()) {
        closePcapHandle();
      }

      enabled = false;
      log.info("Pcap capture stopped");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      log.warn("Error during pcap capture stop: {}", e.getMessage());
      log.debug("Stop error details:", e);
    }
  }

  /**
   * Suspends pcap capture temporarily without losing configuration. The capture can be resumed
   * later with {@link #resume()} using the same ports and settings.
   *
   * <p>If capture is not running, this is a no-op.
   */
  public synchronized void suspend() {
    if (!enabled) {
      log.trace("Capture not active; skipping suspend()");
      return;
    }

    try {
      shouldStop = true;
      waitForReader();

      if (currentDumper != null) {
        currentDumper.close();
        currentDumper = null;
      }

      if (pcapHandle != null && pcapHandle.isOpen()) {
        closePcapHandle();
      }

      enabled = false;
      needsLazyDumper = false;
      log.info("Pcap capture suspended");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      log.warn("Error during pcap capture suspend: {}", e.getMessage());
      log.debug("Suspend error details:", e);
    }
  }

  /**
   * Resumes pcap capture after a suspension with {@link #suspend()}. Uses the same ports and
   * configuration that were active before suspension.
   *
   * <p>When resumed, a dumper is lazily created on the first packet arrival. This ensures files are
   * only created if traffic is actually captured.
   *
   * <p>If capture is already running or has never been started, this is a no-op.
   */
  public synchronized void resume() {
    if (enabled) {
      log.trace("Capture already active; skipping resume()");
      return;
    }

    if (lastStartPorts == null) {
      log.warn("Cannot resume: capture has never been started");
      return;
    }

    needsLazyDumper = true;
    log.info("Resuming pcap capture with cached ports: {}", lastStartPorts);
    start(lastStartPorts);
  }

  private void waitForReader() throws InterruptedException {
    if (readerThread != null && readerThread.isAlive()) {
      readerThread.join(5000);
      if (readerThread.isAlive()) {
        log.warn("Reader thread did not terminate within 5s; some packets may be lost");
      }
    }
  }

  private void closePcapHandle() {
    logDropsIfAny();
    pcapHandle.close();
    pcapHandle = null;
  }

  /** Logs a WARN if the kernel has dropped packets since the last check. */
  private void logDropsIfAny() {
    try {
      PcapStat stats = pcapHandle.getStats();
      if (stats.getNumPacketsDropped() > 0 || stats.getNumPacketsDroppedByIf() > 0) {
        log.warn(
            "Pcap capture is dropping packets: {} dropped by kernel buffer, {} dropped by"
                + " interface (received: {}); consider increasing bufferSizeKb",
            stats.getNumPacketsDropped(),
            stats.getNumPacketsDroppedByIf(),
            stats.getNumPacketsReceived());
      }
    } catch (PcapNativeException | NotOpenException e) {
      log.debug("Could not retrieve pcap stats", e);
    }
  }

  /** Packet capture loop running on the dedicated reader thread. */
  private void captureLoop() {
    long packetCount = 0;
    long lastLogTime = System.currentTimeMillis();

    try {
      log.atInfo()
          .addArgument(shouldStop)
          .addArgument(() -> pcapHandle != null)
          .addArgument(() -> pcapHandle != null && pcapHandle.isOpen())
          .log("Capture loop starting: shouldStop={}, handle={}, isOpen={}");

      while (!shouldStop && pcapHandle != null && pcapHandle.isOpen()) {
        ProcessResult result = processNextPacket();
        if (result.shouldBreak) {
          log.debug("Exiting capture loop due to error or stop signal");
          break;
        }
        if (result.packetProcessed) {
          packetCount++;
        }

        // Log stats every 5 seconds
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 5000) {
          log.debug("Pcap capture: {} packets captured so far", packetCount);
          lastLogTime = now;
        }
      }
      log.debug("Pcap capture loop finished after capturing {} packets", packetCount);
    } catch (NotOpenException e) {
      log.debug("Pcap handle closed during capture loop", e);
    } catch (RuntimeException e) {
      log.warn(
          "Fatal error in pcap capture loop after {} packets: {}", packetCount, e.getMessage());
      log.debug("Capture loop error details:", e);
    } finally {
      log.trace("Pcap capture loop exiting after {} packets", packetCount);
    }
  }

  static class ProcessResult {
    boolean packetProcessed;
    boolean shouldBreak;

    ProcessResult(boolean packetProcessed, boolean shouldBreak) {
      this.packetProcessed = packetProcessed;
      this.shouldBreak = shouldBreak;
    }
  }

  /**
   * Process the next available packet. Returns whether a packet was processed and whether the loop
   * should break.
   */
  private ProcessResult processNextPacket() throws NotOpenException {
    var packet = pcapHandle.getNextPacket();

    if (packet == null) {
      return new ProcessResult(false, false); // Continue waiting
    }

    synchronized (this) {
      if (currentDumper == null) {
        if (needsLazyDumper) {
          createLazyDumper();
        } else {
          log.debug("Packet received but dumper not ready yet, skipping");
          return new ProcessResult(false, false); // Continue waiting for dumper to be ready
        }
      }

      if (currentDumper == null) {
        // Still couldn't create dumper, skip this packet
        return new ProcessResult(false, false);
      }
    }

    return dumpPacket(currentDumper, packet);
  }

  /**
   * Dumps one packet through the given dumper, translating failures into a {@link ProcessResult}.
   */
  ProcessResult dumpPacket(PcapPacketDumper dumper, Packet packet) {
    try {
      dumper.dump(packet);
      return new ProcessResult(true, false); // Packet processed, keep capturing
    } catch (RuntimeException e) {
      log.debug("Error dumping packet", e);
      return new ProcessResult(false, false); // Continue anyway
    }
  }

  private synchronized void createLazyDumper() {
    try {
      Path evidenceDir = Paths.get("target", "evidences");
      if (Files.notExists(evidenceDir)) {
        Files.createDirectories(evidenceDir);
      }

      String filename = String.format("pcap_resumed_%d.pcapng", System.currentTimeMillis());
      Path dumpFile = evidenceDir.resolve(filename);

      currentDumper = new PcapPacketDumper(dumpFile);
      currentDumper.open(pcapHandle);
      needsLazyDumper = false;

      log.info("Created lazy pcap dumper at {}", dumpFile);
    } catch (Exception e) {
      log.warn("Failed to create lazy pcap dumper: {}", e.getMessage());
      log.debug("Lazy dumper creation error:", e);
    }
  }
}
