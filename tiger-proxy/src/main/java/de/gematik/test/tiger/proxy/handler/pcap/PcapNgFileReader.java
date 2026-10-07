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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PcapNgFileReader implements Closeable {

  private static final int SECTION_HEADER_BLOCK = 0x0A0D0D0A;
  private static final int INTERFACE_DESCRIPTION_BLOCK = 1;
  private static final int ENHANCED_PACKET_BLOCK = 6;
  private static final int BYTE_ORDER_MAGIC = 0x1A2B3C4D;

  private static final short OPTION_END = 0;
  private static final short OPTION_INTERFACE_NAME = 2;
  private static final short OPTION_TIMESTAMP_RESOLUTION = 9;
  private static final int DEFAULT_TIMESTAMP_RESOLUTION = 6;

  private static final int ALIGNMENT = 4;
  private static final int BLOCK_HEADER_LENGTH = 8;
  private static final int MIN_BLOCK_LENGTH = 12;
  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private static final int NANOSECOND_EXPONENT = 9;
  // Within an enhanced packet block: after its 8-byte header come the interface id, two timestamp
  // words, the captured length and the original length (4 bytes each), and then the packet data.
  private static final int PACKET_CAPTURED_LENGTH_OFFSET = BLOCK_HEADER_LENGTH + 12;
  private static final int PACKET_DATA_OFFSET = BLOCK_HEADER_LENGTH + 20;

  public record Interface(int linkType, int snaplen, String name, int timestampResolution) {}

  public record Packet(int interfaceId, byte[] data, Timestamp capturedAt, long blockOffset) {}

  private final InputStream in;
  private final List<Interface> interfaces = new ArrayList<>();
  private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
  private long position = 0;

  public PcapNgFileReader(Path file) throws IOException {
    this.in = new BufferedInputStream(Files.newInputStream(file));
  }

  public Interface interfaceOf(int interfaceId) {
    return interfaces.get(interfaceId);
  }

  public ByteOrder byteOrder() {
    return byteOrder;
  }

  public static byte[] readPacketData(FileChannel channel, long blockOffset, ByteOrder byteOrder)
      throws IOException {
    ByteBuffer fixedPart = ByteBuffer.allocate(PACKET_DATA_OFFSET).order(byteOrder);
    readFully(channel, fixedPart, blockOffset);
    int type = fixedPart.getInt(0);
    int total = fixedPart.getInt(BLOCK_HEADER_LENGTH - 4);
    int capturedLength = fixedPart.getInt(PACKET_CAPTURED_LENGTH_OFFSET);
    if (type != ENHANCED_PACKET_BLOCK
        || capturedLength < 0
        || capturedLength > total - PACKET_DATA_OFFSET) {
      throw new IOException("No packet starts at offset " + blockOffset);
    }
    ByteBuffer data = ByteBuffer.allocate(capturedLength);
    readFully(channel, data, blockOffset + PACKET_DATA_OFFSET);
    return data.array();
  }

  private static void readFully(FileChannel channel, ByteBuffer into, long from)
      throws IOException {
    while (into.hasRemaining()) {
      if (channel.read(into, from + into.position()) < 0) {
        throw new EOFException("Truncated pcapng file");
      }
    }
  }

  public Packet next() throws IOException {
    while (true) {
      byte[] header = readOrNull(BLOCK_HEADER_LENGTH);
      if (header == null) {
        return null;
      }
      long blockStart = position;
      if (readInt(header, 0, ByteOrder.LITTLE_ENDIAN) == SECTION_HEADER_BLOCK) {
        readSectionHeader(header);
        continue;
      }

      int type = readInt(header, 0, byteOrder);
      int total = readInt(header, 4, byteOrder);
      if (total < MIN_BLOCK_LENGTH || total % ALIGNMENT != 0) {
        throw new IOException("Not a valid pcapng file: block of length " + total);
      }
      ByteBuffer body = ByteBuffer.wrap(readFully(total - BLOCK_HEADER_LENGTH)).order(byteOrder);
      position += total;
      // The trailing 4 bytes of every block repeat its length; the body proper ends before them.
      if (type == INTERFACE_DESCRIPTION_BLOCK) {
        interfaces.add(parseInterface(body, total - MIN_BLOCK_LENGTH));
      } else if (type == ENHANCED_PACKET_BLOCK) {
        return parsePacket(body, blockStart);
      }
    }
  }

  @Override
  public void close() throws IOException {
    in.close();
  }

  private void readSectionHeader(byte[] header) throws IOException {
    byte[] magic = readFully(4);
    if (readInt(magic, 0, ByteOrder.LITTLE_ENDIAN) == BYTE_ORDER_MAGIC) {
      byteOrder = ByteOrder.LITTLE_ENDIAN;
    } else if (readInt(magic, 0, ByteOrder.BIG_ENDIAN) == BYTE_ORDER_MAGIC) {
      byteOrder = ByteOrder.BIG_ENDIAN;
    } else {
      throw new IOException("Not a valid pcapng file: unknown byte order");
    }
    int total = readInt(header, 4, byteOrder);
    if (total < MIN_BLOCK_LENGTH + 4) {
      throw new IOException("Not a valid pcapng file: section header of length " + total);
    }
    readFully(total - BLOCK_HEADER_LENGTH - 4);
    position += total;
    interfaces.clear(); // interface ids only mean something within one section
  }

  private Interface parseInterface(ByteBuffer body, int bodyLength) {
    int linkType = body.getShort() & 0xFFFF;
    body.getShort(); // reserved
    int snaplen = body.getInt();
    String name = null;
    int resolution = DEFAULT_TIMESTAMP_RESOLUTION;

    while (body.position() + 4 <= bodyLength) {
      short code = body.getShort();
      int length = body.getShort() & 0xFFFF;
      if (code == OPTION_END) {
        break;
      }
      byte[] value = new byte[length];
      body.get(value);
      body.position(body.position() + padding(length));
      if (code == OPTION_INTERFACE_NAME) {
        name = new String(value, StandardCharsets.UTF_8);
      } else if (code == OPTION_TIMESTAMP_RESOLUTION && length >= 1) {
        resolution = value[0];
      }
    }
    return new Interface(linkType, snaplen, name, resolution);
  }

  private Packet parsePacket(ByteBuffer body, long blockStart) throws IOException {
    int interfaceId = body.getInt();
    long high = body.getInt() & 0xFFFFFFFFL;
    long low = body.getInt() & 0xFFFFFFFFL;
    int capturedLength = body.getInt();
    body.getInt(); // original length
    if (interfaceId < 0 || interfaceId >= interfaces.size()) {
      throw new IOException("Not a valid pcapng file: packet for unknown interface " + interfaceId);
    }
    if (capturedLength < 0 || capturedLength > body.remaining()) {
      throw new IOException("Not a valid pcapng file: truncated packet");
    }
    byte[] data = new byte[capturedLength];
    body.get(data);
    long units = high << 32 | low;
    return new Packet(
        interfaceId,
        data,
        toTimestamp(units, interfaces.get(interfaceId).timestampResolution()),
        blockStart);
  }

  private static Timestamp toTimestamp(long units, int resolution) {
    long nanos;
    if ((resolution & 0x80) != 0) {
      // Units are negative powers of two: 2^-n seconds.
      int exponent = resolution & 0x7F;
      nanos = (long) (units * (NANOS_PER_SECOND / Math.pow(2, exponent)));
    } else if (resolution <= NANOSECOND_EXPONENT) {
      nanos = units * pow10(NANOSECOND_EXPONENT - resolution);
    } else {
      nanos = units / pow10(resolution - NANOSECOND_EXPONENT);
    }
    return Timestamp.from(
        Instant.ofEpochSecond(nanos / NANOS_PER_SECOND, nanos % NANOS_PER_SECOND));
  }

  private static long pow10(int exponent) {
    long result = 1;
    for (int i = 0; i < exponent; i++) {
      result *= 10;
    }
    return result;
  }

  private static int padding(int length) {
    return (ALIGNMENT - length % ALIGNMENT) % ALIGNMENT;
  }

  private static int readInt(byte[] bytes, int offset, ByteOrder order) {
    return ByteBuffer.wrap(bytes, offset, 4).order(order).getInt();
  }

  private byte[] readOrNull(int length) throws IOException {
    byte[] bytes = new byte[length];
    int read = in.readNBytes(bytes, 0, length);
    if (read == 0) {
      return null;
    }
    if (read < length) {
      throw new EOFException("Truncated pcapng file");
    }
    return bytes;
  }

  private byte[] readFully(int length) throws IOException {
    byte[] bytes = in.readNBytes(length);
    if (bytes.length < length) {
      throw new EOFException("Truncated pcapng file");
    }
    return bytes;
  }
}
