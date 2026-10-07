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
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.packet.namednumber.DataLinkType;

@DisplayName("PcapPacketDumper")
class PcapPacketDumperTest {

  private static final byte[] RAW_PACKET = {1, 2, 3, 4, 5};
  private static final Timestamp CAPTURED_AT =
      Timestamp.from(Instant.ofEpochSecond(1_700_000_000L, 123_456_789));

  @TempDir private Path tempDir;

  private PcapHandle handle;

  @BeforeEach
  void setUp() throws Exception {
    handle = mock(PcapHandle.class);
    when(handle.getDlt()).thenReturn(DataLinkType.NULL);
    when(handle.getSnapshot()).thenReturn(1500);
  }

  @Test
  @DisplayName("open() creates parent directories if missing")
  void openCreatesParentDirectories() throws Exception {
    Path nestedFile = tempDir.resolve("subdir").resolve("nested").resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(nestedFile);

    dumper.open(handle, "lo");
    dumper.close();

    assertThat(nestedFile).exists();
  }

  @Test
  @DisplayName("open() throws if the dumper is already open")
  void openThrowsIfAlreadyOpen() throws Exception {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));
    dumper.open(handle, "lo");

    assertThatThrownBy(() -> dumper.open(handle, "lo"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already open");
    dumper.close();
  }

  @Test
  @DisplayName("the file carries the interface's link type, snaplen and name")
  void fileDescribesTheInterface() throws Exception {
    Path file = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(file);
    dumper.open(handle, "lo");
    dumper.dumpRaw(RAW_PACKET, CAPTURED_AT);
    dumper.close();

    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      PcapNgFileReader.Packet packet = reader.next();
      assertThat(packet).isNotNull();
      PcapNgFileReader.Interface described = reader.interfaceOf(packet.interfaceId());
      assertThat(described.linkType()).isEqualTo(DataLinkType.NULL.value());
      assertThat(described.snaplen()).isEqualTo(1500);
      assertThat(described.name()).isEqualTo("lo");
    }
  }

  @Test
  @DisplayName("dumpRaw() writes the packet with the time it was captured, not the time of writing")
  void dumpRawKeepsTheCaptureTimestamp() throws Exception {
    Path file = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(file);
    dumper.open(handle, "lo");

    dumper.dumpRaw(RAW_PACKET, CAPTURED_AT);
    dumper.close();

    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      PcapNgFileReader.Packet packet = reader.next();
      assertThat(packet.data()).containsExactly(RAW_PACKET);
      assertThat(packet.capturedAt()).isEqualTo(CAPTURED_AT);
      assertThat(reader.next()).isNull();
    }
  }

  @Test
  @DisplayName("dumpRaw() increments packet count")
  void dumpIncrementsPacketCount() throws Exception {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));
    dumper.open(handle, "lo");
    assertThat(dumper.getPacketCount()).isZero();

    dumper.dumpRaw(RAW_PACKET, CAPTURED_AT);
    assertThat(dumper.getPacketCount()).isOne();

    dumper.dumpRaw(RAW_PACKET, CAPTURED_AT);
    dumper.dumpRaw(RAW_PACKET, CAPTURED_AT);
    assertThat(dumper.getPacketCount()).isEqualTo(3);
    dumper.close();
  }

  @Test
  @DisplayName("dumpRaw() without a timestamp still writes the packet")
  void dumpRawWithoutTimestampStillWrites() throws Exception {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));
    dumper.open(handle, "lo");

    dumper.dumpRaw(RAW_PACKET, null);

    assertThat(dumper.getPacketCount()).isOne();
    dumper.close();
  }

  @Test
  @DisplayName("dumpRaw() is a no-op if not opened")
  void dumpIsNoopIfNotOpen() {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));

    dumper.dumpRaw(RAW_PACKET, CAPTURED_AT);

    assertThat(dumper.getPacketCount()).isZero();
  }

  @Test
  @DisplayName("dumpRaw() after close() loses the packet without throwing")
  void dumpAfterCloseIsLost() throws Exception {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));
    dumper.open(handle, "lo");
    dumper.close();

    assertThatCode(() -> dumper.dumpRaw(RAW_PACKET, CAPTURED_AT)).doesNotThrowAnyException();
    assertThat(dumper.getPacketCount()).isZero();
  }

  @Test
  @DisplayName("close() is safe to call multiple times")
  void closeIsIdempotent() throws Exception {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));
    dumper.open(handle, "lo");

    dumper.close();

    assertThatCode(dumper::close).doesNotThrowAnyException();
    assertThat(Files.size(tempDir.resolve("test.pcapng"))).isPositive();
  }

  @Test
  @DisplayName("getPacketCount returns zero before any packets dumped")
  void getPacketCountInitiallyZero() {
    PcapPacketDumper dumper = new PcapPacketDumper(tempDir.resolve("test.pcapng"));
    assertThat(dumper.getPacketCount()).isZero();
  }
}
