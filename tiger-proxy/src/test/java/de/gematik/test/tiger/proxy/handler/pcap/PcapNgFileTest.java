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

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("pcapng reader and writer")
class PcapNgFileTest {

  private static final int SECTION_HEADER = 0x0A0D0D0A;
  private static final int INTERFACE_DESCRIPTION = 1;
  private static final int ENHANCED_PACKET = 6;
  private static final int BYTE_ORDER_MAGIC = 0x1A2B3C4D;

  @TempDir private Path tempDir;

  @Test
  @DisplayName(
      "packets of several interfaces round-trip with their interface, data and nanoseconds")
  void roundTripsInterfacesPacketsAndTimestamps() throws Exception {
    Path file = tempDir.resolve("round-trip.pcapng");
    Instant first = Instant.ofEpochSecond(1_700_000_000L, 1);
    Instant second = Instant.ofEpochSecond(1_700_000_001L, 999_999_999);
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file)) {
      int loopback = writer.addInterface(0, 0, "lo0");
      int ethernet = writer.addInterface(1, 1500, null);
      writer.writePacket(loopback, new byte[] {1, 2, 3}, Timestamp.from(first));
      writer.writePacket(ethernet, new byte[] {4, 5, 6, 7}, Timestamp.from(second));
    }

    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      PcapNgFileReader.Packet one = reader.next();
      assertThat(one.interfaceId()).isZero();
      assertThat(one.data()).containsExactly(1, 2, 3);
      assertThat(one.capturedAt().toInstant()).isEqualTo(first);
      assertThat(reader.interfaceOf(0)).isEqualTo(new PcapNgFileReader.Interface(0, 0, "lo0", 9));

      PcapNgFileReader.Packet two = reader.next();
      assertThat(two.interfaceId()).isOne();
      assertThat(two.data()).containsExactly(4, 5, 6, 7);
      assertThat(two.capturedAt().toInstant()).isEqualTo(second);
      assertThat(reader.interfaceOf(1).name()).isNull();
      assertThat(reader.interfaceOf(1).linkType()).isOne();
      assertThat(reader.interfaceOf(1).snaplen()).isEqualTo(1500);

      assertThat(reader.next()).isNull();
    }
  }

  @Test
  @DisplayName("the file is a well-formed pcapng file: header, aligned blocks, matching lengths")
  void writesWellFormedBlocks() throws Exception {
    Path file = tempDir.resolve("well-formed.pcapng");
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file)) {
      int id = writer.addInterface(1, 0, "eth0");
      writer.writePacket(id, new byte[] {1, 2, 3, 4, 5}, new Timestamp(0)); // needs padding
    }

    ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
    assertThat(bytes.getInt(0)).isEqualTo(SECTION_HEADER);
    assertThat(bytes.getInt(8)).isEqualTo(BYTE_ORDER_MAGIC);
    int position = 0;
    int blocks = 0;
    while (position < bytes.limit()) {
      int total = bytes.getInt(position + 4);
      assertThat(total % 4).as("block %d is 32-bit aligned", blocks).isZero();
      assertThat(bytes.getInt(position + total - 4))
          .as("block %d repeats its length at its end", blocks)
          .isEqualTo(total);
      position += total;
      blocks++;
    }
    assertThat(position).isEqualTo(bytes.limit());
    assertThat(blocks).as("section header, interface description, one packet").isEqualTo(3);
  }

  @Test
  @DisplayName("a packet can be read again, at random, at the offset the reader reported for it")
  void packetsCanBeReadAgainAtTheirBlockOffset() throws Exception {
    Path file = tempDir.resolve("offsets.pcapng");
    byte[][] payloads = {{1}, {2, 3, 4, 5, 6}, new byte[1000]}; // odd sizes: with padding
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file)) {
      int id = writer.addInterface(1, 0, "eth0");
      for (byte[] payload : payloads) {
        writer.writePacket(id, payload, new Timestamp(0));
      }
    }

    List<PcapNgFileReader.Packet> packets = new ArrayList<>();
    ByteOrder byteOrder;
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      for (PcapNgFileReader.Packet p = reader.next(); p != null; p = reader.next()) {
        packets.add(p);
      }
      byteOrder = reader.byteOrder();
    }

    assertThat(packets).hasSize(3);
    assertThat(packets.get(0).blockOffset())
        .as("after the 28-byte section header and the 40-byte interface block")
        .isEqualTo(68);
    assertThat(packets.get(1).blockOffset())
        .as("after the first packet's 36-byte block (32 bytes of framing, 1 byte padded to 4)")
        .isEqualTo(104);
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      // Read in reverse order, to show it does not depend on where the reader was.
      for (int i = packets.size() - 1; i >= 0; i--) {
        assertThat(
                PcapNgFileReader.readPacketData(channel, packets.get(i).blockOffset(), byteOrder))
            .containsExactly(payloads[i]);
      }
    }
  }

  @Test
  @DisplayName("reading at an offset where no packet starts fails instead of returning garbage")
  void readingWhereNoPacketStartsFails() throws Exception {
    Path file = tempDir.resolve("no-packet.pcapng");
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file)) {
      int id = writer.addInterface(1, 0, "eth0");
      writer.writePacket(id, new byte[] {1, 2, 3}, new Timestamp(0));
    }

    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      assertThatThrownBy(() -> PcapNgFileReader.readPacketData(channel, 0, ByteOrder.LITTLE_ENDIAN))
          .as("offset 0 is the section header, not a packet")
          .isInstanceOf(IOException.class);
      assertThatThrownBy(
              () -> PcapNgFileReader.readPacketData(channel, 100_000, ByteOrder.LITTLE_ENDIAN))
          .as("beyond the end of the file")
          .isInstanceOf(IOException.class);
    }
  }

  @Test
  @DisplayName("an empty file has no packets")
  void emptyFileHasNoPackets() throws Exception {
    Path file = Files.createFile(tempDir.resolve("empty.pcapng"));
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      assertThat(reader.next()).isNull();
    }
  }

  @Test
  @DisplayName("a big-endian file with microsecond timestamps, as many tools write, is read")
  void readsBigEndianMicrosecondFiles() throws Exception {
    ByteArrayOutputStream file = new ByteArrayOutputStream();
    ByteOrder order = ByteOrder.BIG_ENDIAN;
    // Section header: no options.
    ByteBuffer shb = ByteBuffer.allocate(28).order(order);
    shb.putInt(SECTION_HEADER).putInt(28).putInt(BYTE_ORDER_MAGIC);
    shb.putShort((short) 1).putShort((short) 0).putLong(-1L).putInt(28);
    file.write(shb.array());
    // Interface description without a timestamp resolution: microseconds by default.
    ByteBuffer idb = ByteBuffer.allocate(20).order(order);
    idb.putInt(INTERFACE_DESCRIPTION).putInt(20).putShort((short) 1).putShort((short) 0);
    idb.putInt(65535).putInt(20);
    file.write(idb.array());
    // A block this reader does not know, to be skipped.
    ByteBuffer unknown = ByteBuffer.allocate(16).order(order);
    unknown.putInt(0x00000BAD).putInt(16).putInt(0xCAFEBABE).putInt(16);
    file.write(unknown.array());
    // One packet, 2.5 s after the epoch: 2_500_000 microseconds.
    long micros = 2_500_000L;
    ByteBuffer epb = ByteBuffer.allocate(36).order(order);
    epb.putInt(ENHANCED_PACKET).putInt(36).putInt(0);
    epb.putInt((int) (micros >>> 32)).putInt((int) micros);
    epb.putInt(3).putInt(3).put(new byte[] {7, 8, 9}).put((byte) 0).putInt(36);
    file.write(epb.array());
    Path path = tempDir.resolve("big-endian.pcapng");
    Files.write(path, file.toByteArray());

    try (PcapNgFileReader reader = new PcapNgFileReader(path)) {
      PcapNgFileReader.Packet packet = reader.next();
      assertThat(packet.data()).containsExactly(7, 8, 9);
      assertThat(packet.capturedAt().toInstant()).isEqualTo(Instant.ofEpochSecond(2, 500_000_000));
      assertThat(packet.blockOffset())
          .as(
              "after the 28-byte section header, the 20-byte interface block and the 16-byte"
                  + " unknown block")
          .isEqualTo(64);
      assertThat(reader.byteOrder()).isEqualTo(ByteOrder.BIG_ENDIAN);
      assertThat(reader.next()).isNull();

      try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
        assertThat(PcapNgFileReader.readPacketData(channel, 64, ByteOrder.BIG_ENDIAN))
            .containsExactly(7, 8, 9);
      }
    }
  }

  @Test
  @DisplayName("a file that is not pcapng is rejected")
  void rejectsFilesThatAreNotPcapng() throws Exception {
    Path file = tempDir.resolve("not-pcapng.pcapng");
    Files.write(file, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});

    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      assertThatThrownBy(reader::next).isInstanceOf(IOException.class);
    }
  }

  @Test
  @DisplayName("a file cut off in the middle of a block is reported, not silently shortened")
  void reportsTruncatedFiles() throws Exception {
    Path file = tempDir.resolve("truncated.pcapng");
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file)) {
      int id = writer.addInterface(1, 0, "eth0");
      writer.writePacket(id, new byte[] {1, 2, 3, 4}, new Timestamp(0));
    }
    byte[] whole = Files.readAllBytes(file);
    Files.write(file, java.util.Arrays.copyOf(whole, whole.length - 6));

    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      assertThatThrownBy(reader::next).isInstanceOf(IOException.class);
    }
  }

  @Test
  @DisplayName("writing a packet for an interface that was never declared fails")
  void rejectsPacketsForUnknownInterfaces() throws Exception {
    try (PcapNgFileWriter writer = new PcapNgFileWriter(tempDir.resolve("unknown.pcapng"))) {
      assertThatThrownBy(() -> writer.writePacket(0, new byte[] {1}, new Timestamp(0)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
