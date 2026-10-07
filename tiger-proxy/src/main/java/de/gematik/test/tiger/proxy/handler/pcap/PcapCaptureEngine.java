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
package de.gematik.test.tiger.proxy.handler.pcap;

import static de.gematik.rbellogger.util.MemoryConstants.KB;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapStat;

@Slf4j
public abstract class PcapCaptureEngine {

  protected static final int TIMEOUT_MS = 10;
  private static final int MAX_SNAPLEN_BYTES = 65535;

  protected volatile boolean enabled = false;
  protected volatile boolean shouldStop = false;
  protected final List<CaptureSource> sources = new CopyOnWriteArrayList<>();
  @Setter protected List<String> interfaceNames;
  @Setter protected String manualBpfFilter;
  @Setter protected Set<Integer> lastStartPorts;
  @Setter protected boolean dropDuplicatePackets = false;

  protected final int snaplenKb;
  protected final int bufferSizeKb;

  protected static final class CaptureSource {
    public final PcapNetworkInterface iface;
    public final PcapHandle handle;
    public final int snaplenBytes;
    public final int bufferBytes;
    Thread readerThread;

    CaptureSource(
        PcapNetworkInterface iface, PcapHandle handle, int snaplenBytes, int bufferBytes) {
      this.iface = iface;
      this.handle = handle;
      this.snaplenBytes = snaplenBytes;
      this.bufferBytes = bufferBytes;
    }
  }

  protected PcapCaptureEngine(int snaplenKb, int bufferSizeKb) {
    this.snaplenKb = snaplenKb;
    this.bufferSizeKb = bufferSizeKb;
  }

  protected abstract boolean onPacket(CaptureSource source, byte[] rawPacket, Timestamp capturedAt);

  protected abstract void closeAllDumpers();

  protected void onStopped() {}

  protected void onPortsWidened(Set<Integer> newPorts) {}

  public synchronized void alsoCapturePorts(Set<Integer> newPorts) {
    if (!enabled) {
      return;
    }
    onPortsWidened(newPorts);
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
        for (CaptureSource source : sources) {
          source.handle.setFilter(bpfFilter, BpfCompileMode.OPTIMIZE);
        }
        log.debug("Widened pcap BPF filter to: {} on {} interface(s)", bpfFilter, sources.size());
      }
      lastStartPorts = widened.get();
    } catch (PcapNativeException | NotOpenException e) {
      log.warn("Failed to widen pcap BPF filter: {}", e.getMessage());
      log.debug("Filter widening error details:", e);
    }
  }

  public static Optional<Set<Integer>> computeWidenedPorts(
      Set<Integer> currentPorts, Set<Integer> newPorts) {
    if (newPorts == null || newPorts.isEmpty()) {
      return Optional.empty();
    }
    Set<Integer> merged = new TreeSet<>(currentPorts == null ? Set.of() : currentPorts);
    boolean changed = merged.addAll(newPorts);
    return changed ? Optional.of(merged) : Optional.empty();
  }

  public synchronized void start(Set<Integer> ports) {
    try {
      if (enabled) {
        log.trace("Capture already started; skipping");
        return;
      }

      lastStartPorts = ports;
      log.debug(
          "Starting pcap capture service with ports: {} and interfaces: {}", ports, interfaceNames);

      List<PcapNetworkInterface> ifaces = resolveCaptureInterfaces();
      if (ifaces.isEmpty()) {
        String msg =
            interfaceNames == null || interfaceNames.isEmpty()
                ? "No loopback interface found; pcap capture requires one"
                : "Interfaces " + interfaceNames + " not found or no interfaces available";
        throw new PcapNativeException(msg);
      }

      int snaplenBytes = clampSnaplenBytes(snaplenKb);
      int bufferBytes = bufferSizeBytes(bufferSizeKb);
      String bpfFilter = resolveBpfFilter(ports);
      shouldStop = false;

      for (PcapNetworkInterface iface : ifaces) {
        openPcapCapture(iface, snaplenBytes, bufferBytes, bpfFilter);
      }

      if (sources.isEmpty()) {
        throw new PcapNativeException("No capture interface started successfully on: " + ifaces);
      }

      enabled = true;
      for (CaptureSource source : sources) {
        startReader(source);
      }

      log.info(
          "Pcap capture started successfully on {} interface(s): {}",
          sources.size(),
          sources.stream().map(s -> s.iface.getName()).toList());
    } catch (PcapNativeException
        | UnsatisfiedLinkError
        | NoClassDefFoundError
        | SecurityException e) {
      handleStartupFailure("Native pcap library unavailable or permission denied", e);
    } catch (RuntimeException e) {
      handleStartupFailure("Unexpected error during pcap startup", e);
    }
  }

  private void openPcapCapture(
      PcapNetworkInterface iface, int snaplenBytes, int bufferBytes, String bpfFilter) {
    try {
      PcapHandle handle = openFilteredHandle(iface, snaplenBytes, bufferBytes, bpfFilter);
      sources.add(new CaptureSource(iface, handle, snaplenBytes, bufferBytes));
      log.debug("Opened pcap capture on interface '{}'", iface.getName());
    } catch (PcapNativeException | NotOpenException | RuntimeException e) {
      log.warn("Failed to open capture on interface '{}': {}", iface.getName(), e.getMessage());
      log.debug("Interface startup error:", e);
    }
  }

  public String resolveBpfFilter(Set<Integer> ports) {
    return (manualBpfFilter != null && !manualBpfFilter.isBlank())
        ? manualBpfFilter
        : BpfFilterBuilder.forTcpPorts(ports);
  }

  protected List<PcapNetworkInterface> resolveCaptureInterfaces() throws PcapNativeException {
    return resolveInterfaces(interfaceNames);
  }

  protected List<PcapNetworkInterface> resolveInterfaces(List<String> names)
      throws PcapNativeException {
    return PcapNetworkInterfaceResolver.resolveAll(names);
  }

  protected static int clampSnaplenBytes(int snaplenKb) {
    int requestedBytes = snaplenKb * KB;
    if (requestedBytes > 64 * KB) {
      log.warn(
          "Snaplen requested={} KB ({} B) exceeds native max {} B; clamping to {} B",
          snaplenKb,
          requestedBytes,
          MAX_SNAPLEN_BYTES,
          MAX_SNAPLEN_BYTES);
    }
    return Math.min(requestedBytes, MAX_SNAPLEN_BYTES);
  }

  protected static int bufferSizeBytes(int bufferSizeKb) {
    return (int) Math.min((long) bufferSizeKb * KB, Integer.MAX_VALUE);
  }

  protected PcapHandle openHandle(PcapNetworkInterface iface, int snaplenBytes, int bufferBytes)
      throws PcapNativeException {
    PcapHandle.Builder builder =
        new PcapHandle.Builder(iface.getName())
            .snaplen(snaplenBytes)
            .promiscuousMode(PcapNetworkInterface.PromiscuousMode.NONPROMISCUOUS)
            .timeoutMillis(TIMEOUT_MS);
    if (bufferBytes > 0) {
      builder.bufferSize(bufferBytes);
    }
    return builder.build();
  }

  protected PcapHandle openFilteredHandle(
      PcapNetworkInterface iface, int snaplenBytes, int bufferBytes, String bpfFilter)
      throws PcapNativeException, NotOpenException {
    PcapHandle handle = openHandle(iface, snaplenBytes, bufferBytes);
    if (bpfFilter.isEmpty()) {
      return handle;
    }
    try {
      handle.setFilter(bpfFilter, BpfCompileMode.OPTIMIZE);
    } catch (PcapNativeException | NotOpenException | RuntimeException e) {
      handle.close();
      throw e;
    }
    return handle;
  }

  protected void startReader(CaptureSource source) {
    Thread t = new Thread(() -> captureLoop(source), "TigerPcapReader-" + source.iface.getName());
    t.setDaemon(true);
    source.readerThread = t;
    t.start();
  }

  private void handleStartupFailure(String reason, Throwable e) {
    log.warn("Pcap capture startup failed ({}); disabling capture: {}", reason, e.getMessage());
    log.debug("Pcap startup error details:", e);
    enabled = false;
    for (CaptureSource source : sources) {
      try {
        source.handle.close();
      } catch (RuntimeException closeEx) {
        log.debug("Error closing pcap handle after startup failure", closeEx);
      }
    }
    sources.clear();
  }

  public synchronized void stop() {
    if (sources.isEmpty()) {
      log.trace("Capture never started; skipping stop()");
      return;
    }

    try {
      shouldStop = true;
      waitForReaders();

      closeAllDumpers();

      for (CaptureSource source : sources) {
        closeSourceHandle(source);
      }
      sources.clear();
      onStopped();

      enabled = false;
      log.info("Pcap capture stopped");

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException e) {
      log.warn("Error during pcap capture stop: {}", e.getMessage());
      log.debug("Stop error details:", e);
    }
  }

  public boolean isEnabled() {
    return enabled;
  }

  private void waitForReaders() throws InterruptedException {
    for (CaptureSource source : sources) {
      Thread t = source.readerThread;
      if (t != null && t.isAlive()) {
        t.join(5000);
        if (t.isAlive()) {
          log.warn(
              "Reader thread for interface '{}' did not terminate within 5s; some packets may be"
                  + " lost",
              source.iface.getName());
        }
      }
    }
  }

  private void closeSourceHandle(CaptureSource source) {
    logDropsIfAny(source.handle, source.iface.getName());
    if (source.handle.isOpen()) {
      source.handle.close();
    }
  }

  protected void logDropsOnAllSources() {
    for (CaptureSource source : sources) {
      logDropsIfAny(source.handle, source.iface.getName());
    }
  }

  private void logDropsIfAny(PcapHandle handle, String ifaceName) {
    try {
      PcapStat stats = handle.getStats();
      if (stats.getNumPacketsDropped() > 0 || stats.getNumPacketsDroppedByIf() > 0) {
        log.warn(
            "Pcap capture on '{}' is dropping packets: {} dropped by kernel buffer, {} dropped by"
                + " interface (received: {}); consider increasing bufferSizeKb",
            ifaceName,
            stats.getNumPacketsDropped(),
            stats.getNumPacketsDroppedByIf(),
            stats.getNumPacketsReceived());
      }
    } catch (PcapNativeException | NotOpenException e) {
      log.debug("Could not retrieve pcap stats for '{}'", ifaceName, e);
    }
  }

  private void captureLoop(CaptureSource source) {
    long packetCount = 0;
    long lastLogTime = System.currentTimeMillis();
    String ifaceName = source.iface.getName();

    try {
      log.atInfo()
          .addArgument(ifaceName)
          .addArgument(shouldStop)
          .log("Capture loop starting for interface '{}': shouldStop={}");

      while (!shouldStop && source.handle.isOpen()) {
        byte[] rawPacket = source.handle.getNextRawPacket();
        if (rawPacket != null && onPacket(source, rawPacket, source.handle.getTimestamp())) {
          packetCount++;
        }

        long now = System.currentTimeMillis();
        if (now - lastLogTime > 5000) {
          log.debug("Pcap capture on '{}': {} packets captured so far", ifaceName, packetCount);
          lastLogTime = now;
        }
      }
      log.debug(
          "Pcap capture loop for '{}' finished after capturing {} packets", ifaceName, packetCount);
    } catch (NotOpenException e) {
      log.debug("Pcap handle for '{}' closed during capture loop", ifaceName, e);
    } catch (RuntimeException e) {
      log.warn(
          "Fatal error in pcap capture loop for '{}' after {} packets: {}",
          ifaceName,
          packetCount,
          e.getMessage());
      log.debug("Capture loop error details:", e);
    } finally {
      log.trace("Pcap capture loop for '{}' exiting after {} packets", ifaceName, packetCount);
    }
  }

  protected Map<CaptureSource, PcapPacketDumper> openDumpers(
      Path targetFile, Collection<CaptureSource> forSources) {
    Map<CaptureSource, PcapPacketDumper> dumpers = new LinkedHashMap<>();
    for (CaptureSource source : forSources) {
      try {
        PcapPacketDumper dumper = new PcapPacketDumper(tempFileFor(targetFile, source));
        dumper.open(source.handle, source.iface.getName());
        dumpers.put(source, dumper);
      } catch (IOException | RuntimeException e) {
        log.warn(
            "Failed to open per-interface dumper for '{}' on interface '{}': {}",
            targetFile,
            source.iface.getName(),
            e.getMessage());
        log.debug("Per-interface dumper open error:", e);
      }
    }
    if (dumpers.isEmpty()) {
      throw new IllegalStateException(
          "Failed to open a pcap dumper on any interface for " + targetFile);
    }
    return dumpers;
  }

  protected static Path tempFileFor(Path finalTarget, CaptureSource source) {
    String ifaceTag = source.iface.getName().replaceAll("[^\\w.-]+", "_");
    String fileName = "." + finalTarget.getFileName() + "." + ifaceTag + ".tmp";
    Path parent = finalTarget.getParent();
    return parent == null ? Path.of(fileName) : parent.resolve(fileName);
  }

  protected long closeAndMerge(Collection<PcapPacketDumper> dumpers, Path finalTarget) {
    long totalPackets = 0;
    List<File> tempFiles = new ArrayList<>();
    for (PcapPacketDumper dumper : dumpers) {
      totalPackets += dumper.getPacketCount();
      tempFiles.add(dumper.getTargetFile().toFile());
      dumper.close();
    }
    mergeTempFilesInto(tempFiles, finalTarget);
    return totalPackets;
  }

  private void mergeTempFilesInto(List<File> tempFiles, Path finalTarget) {
    try {
      if (tempFiles.size() == 1) {
        Files.move(tempFiles.get(0).toPath(), finalTarget, StandardCopyOption.REPLACE_EXISTING);
        return;
      }
      PcapMerger merger = new PcapMerger();
      merger.setDropDuplicates(dropDuplicatePackets);
      for (File tempFile : tempFiles) {
        merger.addRemotePcap(tempFile.getName(), tempFile, 0L);
      }
      merger.mergeToFile(finalTarget.toFile());
    } catch (IOException | RuntimeException e) {
      log.warn(
          "Failed to merge per-interface pcap captures into {}: {}", finalTarget, e.getMessage());
      log.debug("Merge error details:", e);
    } finally {
      for (File tempFile : tempFiles) {
        try {
          Files.deleteIfExists(tempFile.toPath());
        } catch (IOException ignored) {
          log.debug("Could not delete temp pcap file: {}", tempFile);
        }
      }
    }
  }
}
