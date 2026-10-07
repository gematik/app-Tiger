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

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.packet.namednumber.DataLinkType;

@Slf4j
public final class PcapPacketDumper {

  private static final int ETHERNET = DataLinkType.EN10MB.value();

  private final Path targetFile;
  private PcapNgFileWriter writer;
  private int interfaceId;
  private long packetCount = 0;

  public PcapPacketDumper(Path targetFile) {
    this.targetFile = targetFile;
  }

  public synchronized void open(PcapHandle pcapHandle, String interfaceName) throws IOException {
    if (writer != null) {
      throw new IllegalStateException("Dumper already open");
    }
    writer = new PcapNgFileWriter(targetFile);
    interfaceId = writer.addInterface(linkTypeOf(pcapHandle), snaplenOf(pcapHandle), interfaceName);
    log.debug("Opened pcap dumper: {}", targetFile);
  }

  public synchronized void dumpRaw(byte[] rawPacket, Timestamp capturedAt) {
    if (writer == null) {
      log.debug("Dump called before dumper opened or after it closed; packet lost");
      return;
    }
    try {
      writer.writePacket(
          interfaceId, rawPacket, capturedAt != null ? capturedAt : Timestamp.from(Instant.now()));
      packetCount++;
    } catch (IOException e) {
      log.debug("Could not write a packet to {}; packet lost", targetFile, e);
    }
  }

  public synchronized long getPacketCount() {
    return packetCount;
  }

  public Path getTargetFile() {
    return targetFile;
  }

  public synchronized void close() {
    if (writer != null) {
      try {
        writer.close();
        log.debug("Closed pcap dumper: {}", targetFile);
      } catch (IOException e) {
        log.warn("Error closing pcap dumper: {}", e.getMessage());
      } finally {
        writer = null;
      }
    }
  }

  private static int linkTypeOf(PcapHandle pcapHandle) {
    DataLinkType linkType = pcapHandle.getDlt();
    return linkType != null ? linkType.value() : ETHERNET;
  }

  private static int snaplenOf(PcapHandle pcapHandle) {
    try {
      return pcapHandle.getSnapshot();
    } catch (NotOpenException e) {
      log.debug("Could not read the snapshot length of a closed handle", e);
      return 0;
    }
  }
}
