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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode(of = {"sourceLabel", "currentTimestamp"})
@ToString(exclude = {"reader", "currentPacket", "lookupChannel"})
class PacketCursor implements Comparable<PacketCursor> {
  final String sourceLabel;
  final PcapNgFileReader reader;
  final long clockOffsetMs;
  private final File pcapFile;
  private FileChannel lookupChannel;
  PcapNgFileReader.Packet currentPacket;
  Timestamp currentTimestamp;

  PacketCursor(String sourceLabel, File pcapFile, long clockOffsetMs) throws IOException {
    this.sourceLabel = sourceLabel;
    this.clockOffsetMs = clockOffsetMs;
    this.pcapFile = pcapFile;
    this.reader = new PcapNgFileReader(pcapFile.toPath());
    try {
      advance();
    } catch (IOException e) {
      close();
      throw e;
    }
  }

  void advance() throws IOException {
    currentPacket = reader.next();
    if (currentPacket != null) {
      currentTimestamp =
          Timestamp.from(currentPacket.capturedAt().toInstant().minusMillis(clockOffsetMs));
    }
  }

  PcapNgFileReader.Interface currentInterface() {
    return reader.interfaceOf(currentPacket.interfaceId());
  }

  boolean hasMore() {
    return currentPacket != null;
  }

  byte[] reloadPacketData(long blockOffset) throws IOException {
    if (lookupChannel == null) {
      lookupChannel = FileChannel.open(pcapFile.toPath(), StandardOpenOption.READ);
    }
    return PcapNgFileReader.readPacketData(lookupChannel, blockOffset, reader.byteOrder());
  }

  void close() {
    closeQuietly(reader);
    closeQuietly(lookupChannel);
    lookupChannel = null;
  }

  private void closeQuietly(Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException e) {
      log.debug("Could not close {}: {}", sourceLabel, e.getMessage());
    }
  }

  @Override
  public int compareTo(PacketCursor other) {
    return this.currentTimestamp.compareTo(other.currentTimestamp);
  }
}
