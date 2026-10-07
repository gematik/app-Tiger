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

import de.gematik.test.tiger.proxy.handler.pcap.PcapCaptureEngine;
import de.gematik.test.tiger.proxy.handler.pcap.PcapPacketDumper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScenarioPcapCaptureService extends PcapCaptureEngine {

  @Setter @Getter private static ScenarioPcapCaptureService instance;

  private final Map<Path, Map<CaptureSource, PcapPacketDumper>> extraDumperGroups =
      new ConcurrentHashMap<>();
  private final ThreadLocal<Path> currentScenarioDumper = new ThreadLocal<>();
  private final Set<Path> suspendedScenarioDumpers = ConcurrentHashMap.newKeySet();
  private Map<CaptureSource, PcapPacketDumper> currentDumperGroup;
  private Path currentDumperTarget;

  private volatile boolean needsLazyDumper = false;

  public ScenarioPcapCaptureService(int snaplenKb, int bufferSizeKb) {
    super(snaplenKb, bufferSizeKb);
  }

  public Optional<Path> currentScenarioFile() {
    return Optional.ofNullable(currentScenarioDumper.get());
  }

  Set<Integer> getLastStartPortsForTesting() {
    return lastStartPorts;
  }

  public synchronized void rotate(Path targetFile) {
    if (!enabled) {
      log.trace("Capture not active; skipping rotate() to {}", targetFile);
      return;
    }

    try {
      logDropsOnAllSources();
      openNewGroupAndCloseCurrent(targetFile);
      log.debug("Rotated pcap dumper to {}", targetFile);
    } catch (RuntimeException e) {
      log.warn(
          "Failed to rotate pcap dumper to {}; disabling capture: {}", targetFile, e.getMessage());
      log.debug("Rotation error details:", e);
      enabled = false;
    }
  }

  public synchronized void openDumper(Path targetFile, boolean startSuspended) {
    if (sources.isEmpty()) {
      log.trace("Capture never started; skipping openDumper() for {}", targetFile);
      return;
    }
    currentScenarioDumper.set(targetFile);
    if (startSuspended) {
      suspendedScenarioDumpers.add(targetFile);
      log.debug("Registered pcap dumper (suspended, opened lazily on resume): {}", targetFile);
      return;
    }
    try {
      Map<CaptureSource, PcapPacketDumper> group = openDumpers(targetFile, sources);
      extraDumperGroups.put(targetFile, group);
      log.debug("Opened fan-out pcap dumper: {} ({} interface(s))", targetFile, group.size());
    } catch (RuntimeException e) {
      log.warn("Failed to open pcap dumper {}: {}", targetFile, e.getMessage());
      log.debug("Dumper open error details:", e);
    }
  }

  public synchronized long closeDumper(Path targetFile) {
    forgetScenarioDumper(targetFile);
    Map<CaptureSource, PcapPacketDumper> group = extraDumperGroups.remove(targetFile);
    if (group == null) {
      log.debug("Closed pcap dumper: {} (never opened — no traffic to merge)", targetFile);
      return 0;
    }
    long count = closeAndMerge(group.values(), targetFile);
    log.debug("Closed fan-out pcap dumper: {} ({} packets)", targetFile, count);
    return count;
  }

  private void forgetScenarioDumper(Path targetFile) {
    suspendedScenarioDumpers.remove(targetFile);
    if (targetFile.equals(currentScenarioDumper.get())) {
      currentScenarioDumper.remove();
    }
  }

  public synchronized List<File> closeDumperRawFiles(Path targetFile) {
    forgetScenarioDumper(targetFile);
    Map<CaptureSource, PcapPacketDumper> group = extraDumperGroups.remove(targetFile);
    if (group == null) {
      return List.of();
    }
    List<File> tempFiles = new ArrayList<>();
    for (PcapPacketDumper dumper : group.values()) {
      tempFiles.add(dumper.getTargetFile().toFile());
      dumper.close();
    }
    log.debug(
        "Closed fan-out pcap dumper (raw): {} ({} interface temp file(s), not yet merged)",
        targetFile,
        tempFiles.size());
    return tempFiles;
  }

  private synchronized void openNewGroupAndCloseCurrent(Path targetFile) {
    Map<CaptureSource, PcapPacketDumper> previousGroup = currentDumperGroup;
    Path previousTarget = currentDumperTarget;

    Map<CaptureSource, PcapPacketDumper> newGroup;
    try {
      newGroup = openDumpers(targetFile, sources);
    } catch (RuntimeException openEx) {
      if (previousGroup != null) {
        closeAndMerge(previousGroup.values(), previousTarget);
      }
      currentDumperGroup = null;
      currentDumperTarget = null;
      throw openEx;
    }

    currentDumperGroup = newGroup;
    currentDumperTarget = targetFile;

    if (previousGroup != null) {
      closeAndMerge(previousGroup.values(), previousTarget);
    }
  }

  public synchronized long getCurrentDumperPacketCount() {
    if (currentDumperGroup == null) {
      return 0;
    }
    long total = 0;
    for (PcapPacketDumper dumper : currentDumperGroup.values()) {
      total += dumper.getPacketCount();
    }
    return total;
  }

  @Override
  protected void closeAllDumpers() {
    if (currentDumperGroup != null) {
      closeAndMerge(currentDumperGroup.values(), currentDumperTarget);
      currentDumperGroup = null;
      currentDumperTarget = null;
    }

    for (Map.Entry<Path, Map<CaptureSource, PcapPacketDumper>> entry :
        new ArrayList<>(extraDumperGroups.entrySet())) {
      closeAndMerge(entry.getValue().values(), entry.getKey());
    }
    extraDumperGroups.clear();
    suspendedScenarioDumpers.clear();
  }

  public synchronized void suspend() {
    if (!enabled) {
      log.trace("Capture not active; skipping suspend()");
      return;
    }
    Path target = currentScenarioDumper.get();
    if (target != null) {
      suspendedScenarioDumpers.add(target);
      log.info("Pcap capture suspended for scenario dumper {}", target);
      return;
    }
    enabled = false;
    log.info("Pcap capture suspended");
  }

  public synchronized void resume() {
    if (sources.isEmpty()) {
      log.warn("Cannot resume: capture has never been started");
      return;
    }
    Path target = currentScenarioDumper.get();
    if (target != null) {
      if (!enabled) {
        log.trace("Global capture not active; skipping resume() for scenario dumper {}", target);
        return;
      }
      if (!suspendedScenarioDumpers.remove(target)) {
        log.trace("Scenario dumper {} already active; skipping resume()", target);
        return;
      }
      if (!extraDumperGroups.containsKey(target)) {
        try {
          Map<CaptureSource, PcapPacketDumper> group = openDumpers(target, sources);
          extraDumperGroups.put(target, group);
        } catch (RuntimeException e) {
          log.warn(
              "Failed to open pcap dumper for scenario {} on resume: {}", target, e.getMessage());
          log.debug("Lazy scenario dumper open error:", e);
          return;
        }
      }
      log.info("Pcap capture resumed for scenario dumper {}", target);
      return;
    }
    if (enabled) {
      log.trace("Capture already active; skipping resume()");
      return;
    }
    if (currentDumperGroup == null) {
      needsLazyDumper = true;
    }
    enabled = true;
    log.info("Pcap capture resumed");
  }

  @Override
  public boolean isEnabled() {
    Path target = currentScenarioDumper.get();
    if (target != null) {
      return enabled && !suspendedScenarioDumpers.contains(target);
    }
    return enabled;
  }

  @Override
  protected boolean onPacket(CaptureSource source, byte[] rawPacket, Timestamp capturedAt) {
    if (!enabled) {
      return false;
    }

    boolean processedAny = false;
    boolean anyScenarioDumperActive = false;
    for (Map.Entry<Path, Map<CaptureSource, PcapPacketDumper>> entry :
        extraDumperGroups.entrySet()) {
      anyScenarioDumperActive = true;
      if (suspendedScenarioDumpers.contains(entry.getKey())) {
        continue;
      }
      PcapPacketDumper dumper = entry.getValue().get(source);
      if (dumper != null) {
        processedAny |= dumpPacket(dumper, rawPacket, capturedAt);
      }
    }

    if (anyScenarioDumperActive) {
      return processedAny;
    }

    PcapPacketDumper dumperForThisPacket;
    synchronized (this) {
      if (currentDumperGroup == null && needsLazyDumper) {
        createLazyDumperGroup();
      }
      dumperForThisPacket = currentDumperGroup == null ? null : currentDumperGroup.get(source);
    }

    if (dumperForThisPacket == null) {
      log.debug(
          "Packet received but no dumper ready yet for '{}', skipping", source.iface.getName());
      return false;
    }

    return dumpPacket(dumperForThisPacket, rawPacket, capturedAt);
  }

  boolean dumpPacket(PcapPacketDumper dumper, byte[] rawPacket, Timestamp capturedAt) {
    try {
      dumper.dumpRaw(rawPacket, capturedAt);
      return true;
    } catch (RuntimeException e) {
      log.debug("Error dumping packet", e);
      return false;
    }
  }

  private synchronized void createLazyDumperGroup() {
    try {
      Path evidenceDir = Paths.get("target", "evidences");
      if (Files.notExists(evidenceDir)) {
        Files.createDirectories(evidenceDir);
      }

      String filename = String.format("pcap_resumed_%d.pcapng", System.currentTimeMillis());
      Path dumpFile = evidenceDir.resolve(filename);

      currentDumperGroup = openDumpers(dumpFile, sources);
      currentDumperTarget = dumpFile;
      needsLazyDumper = false;

      log.info("Created lazy pcap dumper at {}", dumpFile);
    } catch (IOException | RuntimeException e) {
      log.warn("Failed to create lazy pcap dumper: {}", e.getMessage());
      log.debug("Lazy dumper creation error:", e);
    }
  }
}
