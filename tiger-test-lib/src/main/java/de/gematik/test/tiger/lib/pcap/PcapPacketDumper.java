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
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapDumper;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.packet.Packet;

/**
 * Wrapper around pcap4j's {@code PcapDumper} for streaming packets to a `.pcapng` file on disk.
 *
 * <p>Ensures the parent directory exists before opening, and handles per-packet writes without
 * loading the full file into memory.
 */
@Slf4j
final class PcapPacketDumper {

  private final Path targetFile;
  private PcapDumper dumper;
  private long packetCount = 0;

  /**
   * Creates a dumper for the given target file path.
   *
   * @param targetFile destination `.pcapng` file path.
   */
  PcapPacketDumper(Path targetFile) {
    this.targetFile = targetFile;
  }

  /**
   * Opens the dumper, creating parent directories if needed.
   *
   * @param pcapHandle the open pcap handle to associate with this dumper.
   * @throws Exception if the file cannot be created or the dumper cannot be opened.
   */
  void open(PcapHandle pcapHandle) throws Exception {
    if (dumper != null) {
      throw new IllegalStateException("Dumper already open");
    }

    // Ensure parent directory exists.
    Path parentDir = targetFile.getParent();
    if (parentDir != null && Files.notExists(parentDir)) {
      Files.createDirectories(parentDir);
      log.debug("Created pcap output directory: {}", parentDir);
    }

    try {
      dumper = pcapHandle.dumpOpen(targetFile.toString());
      log.debug("Opened pcap dumper: {}", targetFile);
    } catch (PcapNativeException e) {
      log.error("Failed to open pcap dumper to {}: {}", targetFile, e.getMessage());
      throw e;
    }
  }

  /**
   * Dumps a single packet to the file. Gracefully handles dumper closure to prevent interference
   * with test execution. Lost packets are logged but not propagated.
   *
   * @param packet the packet to write.
   */
  void dump(Packet packet) {
    if (dumper != null) {
      try {
        dumper.dump(packet);
        packetCount++;
      } catch (NotOpenException e) {
        log.debug("Dumper closed during write; packet lost", e);
      }
    } else {
      log.debug("Dump called before dumper opened; packet lost");
    }
  }

  /** Number of packets successfully written since this dumper was opened. */
  long getPacketCount() {
    return packetCount;
  }

  /**
   * Flushes and closes the dumper, allowing it to be garbage collected.
   *
   * <p>Safe to call multiple times (subsequent calls are no-ops).
   */
  void close() {
    if (dumper != null) {
      try {
        dumper.flush();
        dumper.close();
        log.debug("Closed pcap dumper: {}", targetFile);
      } catch (Exception e) {
        log.warn("Error closing pcap dumper: {}", e.getMessage());
      } finally {
        dumper = null;
      }
    }
  }
}
