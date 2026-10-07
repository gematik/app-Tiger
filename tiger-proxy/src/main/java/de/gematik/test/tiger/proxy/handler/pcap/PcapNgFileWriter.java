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

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;

public final class PcapNgFileWriter implements Closeable {

  private static final int SECTION_HEADER_BLOCK = 0x0A0D0D0A;
  private static final int INTERFACE_DESCRIPTION_BLOCK = 1;
  private static final int ENHANCED_PACKET_BLOCK = 6;
  private static final int BYTE_ORDER_MAGIC = 0x1A2B3C4D;

  private static final short OPTION_END = 0;
  private static final short OPTION_INTERFACE_NAME = 2;
  private static final short OPTION_TIMESTAMP_RESOLUTION = 9;
  private static final byte NANOSECOND_RESOLUTION = 9;

  private static final int ALIGNMENT = 4;
  private static final int SECTION_HEADER_LENGTH = 28;
  private static final int BLOCK_FRAME_LENGTH = 12; // type + leading length + trailing length
  private static final int INTERFACE_FIXED_LENGTH = BLOCK_FRAME_LENGTH + 8;
  private static final int PACKET_FIXED_LENGTH = BLOCK_FRAME_LENGTH + 20;
  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final OutputStream out;
  private int interfaceCount = 0;

  public PcapNgFileWriter(Path file) throws IOException {
    Path parent = file.getParent();
    if (parent != null && Files.notExists(parent)) {
      Files.createDirectories(parent);
    }
    this.out = new BufferedOutputStream(Files.newOutputStream(file));
    writeSectionHeader();
  }

  public int addInterface(int linkType, int snaplen, String name) throws IOException {
    byte[] nameBytes = name == null ? new byte[0] : name.getBytes(StandardCharsets.UTF_8);
    int nameOptionLength = nameBytes.length == 0 ? 0 : 4 + padded(nameBytes.length);
    int optionsLength = nameOptionLength + 8 /* if_tsresol */ + 4 /* end of options */;
    int total = INTERFACE_FIXED_LENGTH + optionsLength;

    ByteBuffer block = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
    block.putInt(INTERFACE_DESCRIPTION_BLOCK).putInt(total);
    block.putShort((short) linkType).putShort((short) 0).putInt(snaplen);
    if (nameBytes.length > 0) {
      putOption(block, OPTION_INTERFACE_NAME, nameBytes);
    }
    putOption(block, OPTION_TIMESTAMP_RESOLUTION, new byte[] {NANOSECOND_RESOLUTION});
    block.putShort(OPTION_END).putShort((short) 0);
    block.putInt(total);
    out.write(block.array());
    return interfaceCount++;
  }

  public void writePacket(int interfaceId, byte[] data, Timestamp capturedAt) throws IOException {
    if (interfaceId < 0 || interfaceId >= interfaceCount) {
      throw new IllegalArgumentException("Unknown interface id " + interfaceId);
    }
    Instant instant = capturedAt.toInstant();
    long nanos = instant.getEpochSecond() * NANOS_PER_SECOND + instant.getNano();

    int total = PACKET_FIXED_LENGTH + padded(data.length);
    ByteBuffer block = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
    block.putInt(ENHANCED_PACKET_BLOCK).putInt(total);
    block.putInt(interfaceId).putInt((int) (nanos >>> 32)).putInt((int) nanos);
    block.putInt(data.length).putInt(data.length);
    block.put(data);
    block.position(total - 4);
    block.putInt(total);
    out.write(block.array());
  }

  public void flush() throws IOException {
    out.flush();
  }

  @Override
  public void close() throws IOException {
    out.close();
  }

  private void writeSectionHeader() throws IOException {
    ByteBuffer block = ByteBuffer.allocate(SECTION_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
    block.putInt(SECTION_HEADER_BLOCK).putInt(SECTION_HEADER_LENGTH);
    block.putInt(BYTE_ORDER_MAGIC).putShort((short) 1).putShort((short) 0);
    block.putLong(-1L); // section length: not known up front
    block.putInt(SECTION_HEADER_LENGTH);
    out.write(block.array());
  }

  private static void putOption(ByteBuffer block, short code, byte[] value) {
    block.putShort(code).putShort((short) value.length).put(value);
    block.position(block.position() + padded(value.length) - value.length);
  }

  private static int padded(int length) {
    return (length + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT;
  }
}
