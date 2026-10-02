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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapDumper;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.packet.Packet;

@DisplayName("PcapPacketDumper")
class PcapPacketDumperTest {

  @TempDir private Path tempDir;

  @Mock private PcapHandle mockPcapHandle;
  @Mock private PcapDumper mockPcapDumper;
  @Mock private Packet mockPacket;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @DisplayName("Constructor stores target file path")
  void constructorStoresTargetFile() {
    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    assertThat(dumper).isNotNull();
  }

  @Test
  @DisplayName("open() creates parent directories if missing")
  void openCreatesParentDirectories() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);

    Path nestedFile = tempDir.resolve("subdir").resolve("nested").resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(nestedFile);

    dumper.open(mockPcapHandle);

    assertThat(Files.exists(nestedFile.getParent())).isTrue();
    verify(mockPcapHandle).dumpOpen(nestedFile.toString());
  }

  @Test
  @DisplayName("open() calls pcapHandle.dumpOpen with correct path")
  void openCallsDumpOpen() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);

    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    dumper.open(mockPcapHandle);

    verify(mockPcapHandle).dumpOpen(targetFile.toString());
  }

  @Test
  @DisplayName("open() throws if dumper already open")
  void openThrowsIfAlreadyOpen() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);

    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    dumper.open(mockPcapHandle);

    assertThatThrownBy(() -> dumper.open(mockPcapHandle))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already open");
  }

  @Test
  @DisplayName("dump() increments packet count")
  void dumpIncrementsPacketCount() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);

    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    dumper.open(mockPcapHandle);

    assertThat(dumper.getPacketCount()).isZero();

    dumper.dump(mockPacket);
    assertThat(dumper.getPacketCount()).isOne();

    dumper.dump(mockPacket);
    dumper.dump(mockPacket);
    assertThat(dumper.getPacketCount()).isEqualTo(3);

    verify(mockPcapDumper, times(3)).dump(any(Packet.class));
  }

  @Test
  @DisplayName("dump() is no-op if not opened")
  void dumpIsNoopIfNotOpen() throws Exception {
    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);

    dumper.dump(mockPacket);
    assertThat(dumper.getPacketCount()).isZero();
  }

  @Test
  @DisplayName("dump() silently handles NotOpenException from inner dumper")
  void dumpHandlesNotOpenException() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);
    doThrow(new NotOpenException("closed")).when(mockPcapDumper).dump(any(Packet.class));

    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    dumper.open(mockPcapHandle);

    dumper.dump(mockPacket); // Should not throw, logs and continues
    assertThat(dumper.getPacketCount()).isZero(); // Packet was not counted
  }

  @Test
  @DisplayName("close() calls dumper.close() and clears reference")
  void closeCallsDumperClose() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);

    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    dumper.open(mockPcapHandle);

    dumper.close();

    verify(mockPcapDumper).close();
  }

  @Test
  @DisplayName("close() is safe to call multiple times")
  void closeIsIdempotent() throws Exception {
    when(mockPcapHandle.dumpOpen(anyString())).thenReturn(mockPcapDumper);

    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    dumper.open(mockPcapHandle);

    dumper.close();
    dumper.close(); // Should not throw

    verify(mockPcapDumper, times(1)).close();
  }

  @Test
  @DisplayName("getPacketCount returns zero before any packets dumped")
  void getPacketCountInitiallyZero() {
    Path targetFile = tempDir.resolve("test.pcapng");
    PcapPacketDumper dumper = new PcapPacketDumper(targetFile);
    assertThat(dumper.getPacketCount()).isZero();
  }
}
