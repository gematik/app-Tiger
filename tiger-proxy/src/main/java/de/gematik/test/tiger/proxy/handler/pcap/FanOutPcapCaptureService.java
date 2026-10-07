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

import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.BpfProgram;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;

@Slf4j
public class FanOutPcapCaptureService extends PcapCaptureEngine {

  private final Map<String, Capture> captureDumpers = new ConcurrentHashMap<>();
  private final Set<String> kernelFilterParts = new LinkedHashSet<>();
  private boolean kernelUnrestricted = false;

  private static final class CaptureFilter {
    final String manualFilter;
    volatile Set<Integer> ports;
    volatile Map<CaptureSource, BpfProgram> programs = Map.of();

    CaptureFilter(String manualFilter, Set<Integer> ports) {
      this.manualFilter = manualFilter;
      this.ports = ports;
    }

    String expression() {
      return manualFilter != null ? manualFilter : BpfFilterBuilder.forTcpPorts(ports);
    }

    boolean accepts(CaptureSource source, byte[] rawPacket) {
      BpfProgram program = programs.get(source);
      return program == null || program.applyFilter(rawPacket);
    }
  }

  private static final class Capture {
    final Path targetFile;
    final boolean isGap;
    final String suiteId;
    final AtomicBoolean suspended = new AtomicBoolean(false);
    final Map<CaptureSource, PcapPacketDumper> dumpers;
    volatile CaptureFilter filter;

    Capture(
        Path targetFile,
        boolean isGap,
        String suiteId,
        Map<CaptureSource, PcapPacketDumper> dumpers) {
      this.targetFile = targetFile;
      this.isGap = isGap;
      this.suiteId = suiteId;
      this.dumpers = dumpers;
    }

    long packetCount() {
      long total = 0;
      for (PcapPacketDumper dumper : dumpers.values()) {
        total += dumper.getPacketCount();
      }
      return total;
    }
  }

  public FanOutPcapCaptureService(int snaplenKb, int bufferSizeKb) {
    super(snaplenKb, bufferSizeKb);
  }

  public FanOutPcapCaptureService() {
    this(64, 16 * KB);
  }

  public synchronized boolean openCapture(String captureId, Path targetFile, boolean isGap) {
    if (!enabled) {
      log.trace("Capture not active; skipping openCapture({}) to {}", captureId, targetFile);
      return false;
    }

    try {
      Map<CaptureSource, PcapPacketDumper> dumpers = openDumpers(targetFile, sources);
      captureDumpers.put(captureId, new Capture(targetFile, isGap, null, dumpers));
      log.debug(
          "Opened pcap capture {} -> {} (gap={}, {} interface(s))",
          captureId,
          targetFile,
          isGap,
          dumpers.size());
      return true;
    } catch (RuntimeException e) {
      log.warn("Failed to open pcap capture {} to {}: {}", captureId, targetFile, e.getMessage());
      log.debug("Capture open error details:", e);
      return false;
    }
  }

  public boolean openCapture(
      String captureId,
      Path targetFile,
      boolean isGap,
      PcapCaptureConfiguration settings,
      Set<Integer> ports) {
    return openCapture(captureId, targetFile, isGap, settings, ports, null);
  }

  public synchronized boolean openCapture(
      String captureId,
      Path targetFile,
      boolean isGap,
      PcapCaptureConfiguration settings,
      Set<Integer> ports,
      String suiteId) {
    try {
      String manual = settings.getBpfFilter();
      CaptureFilter filter =
          new CaptureFilter(
              manual != null && !manual.isBlank() ? manual : null,
              ports == null ? Set.of() : new TreeSet<>(ports));

      List<CaptureSource> wanted = ensureSources(settings);
      compileCaptureFilter(filter, wanted);
      if (widenKernelFilter(filter.expression())) {
        applyKernelFilter();
      }

      Capture capture = new Capture(targetFile, isGap, suiteId, openDumpers(targetFile, wanted));
      capture.filter = filter;
      captureDumpers.put(captureId, capture);
      log.debug(
          "Opened pcap capture {} -> {} (gap={}, interfaces={}, filter='{}')",
          captureId,
          targetFile,
          isGap,
          wanted.stream().map(s -> s.iface.getName()).toList(),
          filter.expression());
      return true;
    } catch (PcapNativeException
        | NotOpenException
        | UnsatisfiedLinkError
        | NoClassDefFoundError
        | RuntimeException e) {
      log.warn("Failed to open pcap capture {} to {}: {}", captureId, targetFile, e.getMessage());
      log.debug("Capture open error details:", e);
      return false;
    }
  }

  private List<CaptureSource> ensureSources(PcapCaptureConfiguration settings)
      throws PcapNativeException {
    List<PcapNetworkInterface> ifaces = resolveRequestedInterfaces(settings);
    int snaplenBytes = clampSnaplenBytes(settings.getSnaplenKb());
    int bufferBytes = bufferSizeBytes(settings.getBufferSizeKb());

    List<CaptureSource> wanted = new ArrayList<>();
    for (PcapNetworkInterface iface : ifaces) {
      CaptureSource source = findSource(iface.getName());
      if (source != null) {
        warnIfSettingsDiffer(source, snaplenBytes, bufferBytes);
      } else {
        source = openSource(iface, snaplenBytes, bufferBytes);
      }
      if (source != null) {
        wanted.add(source);
      }
    }
    if (wanted.isEmpty()) {
      throw new PcapNativeException("No capture interface started successfully on: " + ifaces);
    }
    return wanted;
  }

  private List<PcapNetworkInterface> resolveRequestedInterfaces(PcapCaptureConfiguration settings)
      throws PcapNativeException {
    List<PcapNetworkInterface> ifaces = resolveInterfaces(settings.getInterfaceNames());
    if (ifaces.isEmpty()) {
      throw new PcapNativeException(
          settings.getInterfaceNames() == null || settings.getInterfaceNames().isEmpty()
              ? "No loopback interface found; pcap capture requires one"
              : "Interfaces " + settings.getInterfaceNames() + " not found");
    }
    return ifaces;
  }

  private void warnIfSettingsDiffer(CaptureSource open, int snaplenBytes, int bufferBytes) {
    warnIfDiffers(open, "snaplen", open.snaplenBytes, snaplenBytes);
    warnIfDiffers(open, "buffer", open.bufferBytes, bufferBytes);
  }

  private void warnIfDiffers(CaptureSource open, String setting, int inUse, int asked) {
    if (inUse != asked) {
      log.warn(
          "Interface '{}' is already being captured with a {} of {} B; keeping that"
              + " instead of the {} B this capture asked for (fixed when the interface opens)",
          open.iface.getName(),
          setting,
          inUse,
          asked);
    }
  }

  private CaptureSource openSource(PcapNetworkInterface iface, int snaplenBytes, int bufferBytes) {
    try {
      PcapHandle handle =
          openFilteredHandle(iface, snaplenBytes, bufferBytes, kernelFilterExpression());
      CaptureSource source = new CaptureSource(iface, handle, snaplenBytes, bufferBytes);
      shouldStop = false;
      enabled = true;
      sources.add(source);
      startReader(source);
      log.debug("Opened pcap capture on interface '{}'", iface.getName());
      return source;
    } catch (PcapNativeException | NotOpenException | RuntimeException e) {
      log.warn("Failed to open capture on interface '{}': {}", iface.getName(), e.getMessage());
      log.debug("Interface startup error:", e);
      return null;
    }
  }

  private CaptureSource findSource(String interfaceName) {
    return sources.stream()
        .filter(s -> s.iface.getName().equals(interfaceName))
        .findFirst()
        .orElse(null);
  }

  private boolean widenKernelFilter(String expression) {
    if (kernelUnrestricted) {
      return false;
    }
    if (expression.isEmpty()) {
      kernelUnrestricted = true;
      return true;
    }
    return kernelFilterParts.add(expression);
  }

  private String kernelFilterExpression() {
    if (kernelUnrestricted || kernelFilterParts.isEmpty()) {
      return "";
    }
    if (kernelFilterParts.size() == 1) {
      return kernelFilterParts.iterator().next();
    }
    return kernelFilterParts.stream().map(p -> "(" + p + ")").collect(Collectors.joining(" or "));
  }

  private void applyKernelFilter() {
    String expression = kernelFilterExpression();
    for (CaptureSource source : sources) {
      try {
        source.handle.setFilter(expression, BpfCompileMode.OPTIMIZE);
      } catch (PcapNativeException | NotOpenException e) {
        log.warn(
            "Failed to widen the BPF filter on interface '{}': {}",
            source.iface.getName(),
            e.getMessage());
        log.debug("Filter widening error details:", e);
      }
    }
  }

  private void compileCaptureFilter(CaptureFilter filter, Iterable<CaptureSource> forSources)
      throws PcapNativeException, NotOpenException {
    String expression = filter.expression();
    Map<CaptureSource, BpfProgram> programs = new HashMap<>();
    if (!expression.isEmpty()) {
      for (CaptureSource source : forSources) {
        programs.put(
            source,
            source.handle.compileFilter(
                expression, BpfCompileMode.OPTIMIZE, PcapHandle.PCAP_NETMASK_UNKNOWN));
      }
    }
    filter.programs = programs;
  }

  @Override
  protected void onPortsWidened(Set<Integer> newPorts) {
    for (Capture capture : captureDumpers.values()) {
      CaptureFilter filter = capture.filter;
      if (filter == null || filter.manualFilter != null || filter.ports.isEmpty()) {
        continue;
      }
      Optional<Set<Integer>> widened = computeWidenedPorts(filter.ports, newPorts);
      if (widened.isEmpty()) {
        continue;
      }
      try {
        filter.ports = widened.get();
        compileCaptureFilter(filter, capture.dumpers.keySet());
        if (widenKernelFilter(filter.expression())) {
          applyKernelFilter();
        }
      } catch (PcapNativeException | NotOpenException e) {
        log.warn("Failed to widen a pcap capture's filter: {}", e.getMessage());
        log.debug("Capture filter widening error details:", e);
      }
    }
  }

  public synchronized long closeCapture(String captureId) {
    Capture capture = captureDumpers.remove(captureId);
    if (capture == null) {
      log.debug("Closed pcap capture {} (never opened)", captureId);
      return 0;
    }
    long count = closeAndMerge(capture.dumpers.values(), capture.targetFile);
    log.debug("Closed pcap capture {} ({} packets)", captureId, count);
    return count;
  }

  public long getCapturePacketCount(String captureId) {
    Capture capture = captureDumpers.get(captureId);
    return capture == null ? 0 : capture.packetCount();
  }

  public boolean suspendCapture(String captureId) {
    Capture capture = captureDumpers.get(captureId);
    if (capture == null) {
      log.trace("Cannot suspend unknown pcap capture {}", captureId);
      return false;
    }
    capture.suspended.set(true);
    log.debug("Suspended pcap capture {}", captureId);
    return true;
  }

  public boolean resumeCapture(String captureId) {
    Capture capture = captureDumpers.get(captureId);
    if (capture == null) {
      log.trace("Cannot resume unknown pcap capture {}", captureId);
      return false;
    }
    capture.suspended.set(false);
    log.debug("Resumed pcap capture {}", captureId);
    return true;
  }

  @Override
  protected void closeAllDumpers() {
    for (String captureId : new ArrayList<>(captureDumpers.keySet())) {
      closeCapture(captureId);
    }
  }

  @Override
  protected void onStopped() {
    kernelFilterParts.clear();
    kernelUnrestricted = false;
  }

  @Override
  protected boolean onPacket(CaptureSource source, byte[] rawPacket, Timestamp capturedAt) {
    if (captureDumpers.isEmpty()) {
      return false;
    }

    boolean processed = false;
    for (Capture capture : captureDumpers.values()) {
      if (capture.isGap && anyNonGapCaptureOpenFor(capture.suiteId)) {
        continue;
      }
      if (capture.suspended.get()) {
        continue;
      }
      PcapPacketDumper dumper = capture.dumpers.get(source);
      if (dumper == null) {
        continue;
      }
      CaptureFilter filter = capture.filter;
      if (filter != null && !filter.accepts(source, rawPacket)) {
        continue;
      }
      try {
        dumper.dumpRaw(rawPacket, capturedAt);
        processed = true;
      } catch (RuntimeException e) {
        log.debug("Error dumping packet to a pcap capture", e);
      }
    }
    return processed;
  }

  private boolean anyNonGapCaptureOpenFor(String suiteId) {
    return captureDumpers.values().stream()
        .anyMatch(s -> !s.isGap && Objects.equals(s.suiteId, suiteId));
  }
}
