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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PcapMerger")
class PcapMergerTest {

  private static final int ETHERNET = 1;
  private static final int BSD_LOOPBACK = 0;
  private static final Instant BASE = Instant.ofEpochSecond(1_700_000_000L);

  private PcapMerger merger;

  @BeforeEach
  void setUp() {
    merger = new PcapMerger();
  }

  /** One packet as written to or read from a capture file. */
  private record Seen(String interfaceName, int linkType, byte[] data, Instant capturedAt) {}

  private static File capture(
      Path dir, String name, int linkType, String interfaceName, List<Seen> packets)
      throws IOException {
    File file = dir.resolve(name).toFile();
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file.toPath())) {
      int id = writer.addInterface(linkType, 65535, interfaceName);
      for (Seen packet : packets) {
        writer.writePacket(id, packet.data(), Timestamp.from(packet.capturedAt()));
      }
    }
    return file;
  }

  private static Seen packet(int marker, Instant capturedAt) {
    return new Seen(null, 0, new byte[] {(byte) marker}, capturedAt);
  }

  private static List<Seen> read(File file) throws IOException {
    List<Seen> packets = new ArrayList<>();
    try (PcapNgFileReader reader = new PcapNgFileReader(file.toPath())) {
      for (PcapNgFileReader.Packet packet = reader.next(); packet != null; packet = reader.next()) {
        PcapNgFileReader.Interface described = reader.interfaceOf(packet.interfaceId());
        packets.add(
            new Seen(
                described.name(),
                described.linkType(),
                packet.data(),
                packet.capturedAt().toInstant()));
      }
    }
    return packets;
  }

  @Test
  @DisplayName("should handle empty merge gracefully")
  void testEmptyMerge(@TempDir Path tempDir) throws Exception {
    File output = tempDir.resolve("merged.pcapng").toFile();
    merger.mergeToFile(output);
    assertThat(output).exists();
  }

  @Test
  @DisplayName("should handle nonexistent pcap file gracefully")
  void testNonexistentFile(@TempDir Path tempDir) throws Exception {
    File nonexistent = new File("/tmp/nonexistent_" + System.nanoTime() + ".pcapng");
    merger.addRemotePcap("proxy-1", nonexistent, 0L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    merger.mergeToFile(output);
    assertThat(output).exists();
  }

  @Test
  @DisplayName("should accept clock offset as Duration")
  void testClockOffsetDuration(@TempDir Path tempDir) throws Exception {
    File fakeFile = tempDir.resolve("fake.pcapng").toFile();
    Files.createFile(fakeFile.toPath());

    merger.addRemotePcap("proxy-1", fakeFile, Duration.ofMillis(100));

    File output = tempDir.resolve("merged.pcapng").toFile();
    // Should not throw
    merger.mergeToFile(output);
    assertThat(output).exists();
  }

  @Test
  @DisplayName("should accept clock offset in milliseconds")
  void testClockOffsetMillis(@TempDir Path tempDir) throws Exception {
    File fakeFile = tempDir.resolve("fake.pcapng").toFile();
    Files.createFile(fakeFile.toPath());

    merger.addRemotePcap("proxy-1", fakeFile, 50L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    // Should not throw
    merger.mergeToFile(output);
    assertThat(output).exists();
  }

  @Test
  @DisplayName("merged packets are sorted by, and keep, their own capture timestamps")
  void mergedPacketsKeepTheirOwnCaptureTimestamps(@TempDir Path tempDir) throws Exception {
    Instant firstA = BASE.plusSeconds(1).plusNanos(111_111_111);
    Instant firstB = BASE.plusSeconds(2).plusNanos(222_222_222);
    Instant secondA = BASE.plusSeconds(3).plusNanos(333_333_333);
    Instant secondB = BASE.plusSeconds(4).plusNanos(444_444_444);
    File a =
        capture(
            tempDir, "a.pcapng", ETHERNET, "eth0", List.of(packet(1, firstA), packet(3, secondA)));
    File b =
        capture(
            tempDir, "b.pcapng", ETHERNET, "eth1", List.of(packet(2, firstB), packet(4, secondB)));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    long written = merger.mergeToFile(output);

    assertThat(written).isEqualTo(4);
    List<Seen> merged = read(output);
    assertThat(merged)
        .extracting(s -> s.data()[0])
        .containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    assertThat(merged)
        .extracting(Seen::capturedAt)
        .as("each packet keeps the time it was captured at, not the time of the merge")
        .containsExactly(firstA, firstB, secondA, secondB);
  }

  @Test
  @DisplayName("a source's clock offset is taken off the timestamps that are written")
  void clockOffsetIsAppliedToTheWrittenTimestamps(@TempDir Path tempDir) throws Exception {
    File local =
        capture(
            tempDir, "local.pcapng", ETHERNET, "eth0", List.of(packet(1, BASE.plusMillis(1500))));
    // This remote's clock runs 1 s ahead: what it stamped 2 s really happened at 1 s.
    File remote =
        capture(
            tempDir, "remote.pcapng", ETHERNET, "eth0", List.of(packet(2, BASE.plusMillis(2000))));
    merger.addRemotePcap("local", local, 0L);
    merger.addRemotePcap("remote", remote, 1000L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    merger.mergeToFile(output);

    List<Seen> merged = read(output);
    assertThat(merged).extracting(s -> s.data()[0]).containsExactly((byte) 2, (byte) 1);
    assertThat(merged.get(0).capturedAt()).isEqualTo(BASE.plusMillis(1000));
    assertThat(merged.get(1).capturedAt()).isEqualTo(BASE.plusMillis(1500));
  }

  @Test
  @DisplayName("unlike link types share one file, each packet unchanged")
  void unlikeLinkTypesShareOneFileWithoutRewriting(@TempDir Path tempDir) throws Exception {
    byte[] loopbackFrame = {2, 0, 0, 0, 0x45, 1, 2, 3};
    byte[] ethernetFrame = {9, 9, 9, 9, 9, 9, 8, 8, 8, 8, 8, 8, 0x08, 0x00, 0x45};
    File loopback =
        capture(
            tempDir,
            "lo.pcapng",
            BSD_LOOPBACK,
            "lo0",
            List.of(new Seen(null, 0, loopbackFrame, BASE.plusSeconds(1))));
    File ethernet =
        capture(
            tempDir,
            "en.pcapng",
            ETHERNET,
            "en0",
            List.of(new Seen(null, 0, ethernetFrame, BASE.plusSeconds(2))));
    merger.addRemotePcap("lo", loopback, 0L);
    merger.addRemotePcap("en", ethernet, 0L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    merger.mergeToFile(output);

    List<Seen> merged = read(output);
    assertThat(merged).hasSize(2);
    assertThat(merged.get(0).linkType()).isEqualTo(BSD_LOOPBACK);
    assertThat(merged.get(0).data()).containsExactly(loopbackFrame);
    assertThat(merged.get(1).linkType()).isEqualTo(ETHERNET);
    assertThat(merged.get(1).data()).containsExactly(ethernetFrame);
  }

  @Test
  @DisplayName("each source's interface keeps its own, source-identifying name in the output")
  void interfacesAreNamedAfterTheirSource(@TempDir Path tempDir) throws Exception {
    File a =
        capture(tempDir, "a.pcapng", ETHERNET, "eth0", List.of(packet(1, BASE.plusSeconds(1))));
    File b = capture(tempDir, "b.pcapng", ETHERNET, null, List.of(packet(2, BASE.plusSeconds(2))));
    merger.addRemotePcap("proxy-1", a, 0L);
    merger.addRemotePcap("proxy-2", b, 0L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    merger.mergeToFile(output);

    assertThat(read(output))
        .extracting(Seen::interfaceName)
        .containsExactly("proxy-1 / eth0", "proxy-2");
  }

  @Test
  @DisplayName("an already merged file, several interfaces in one, merges again without loss")
  void mergedFileCanBeMergedAgain(@TempDir Path tempDir) throws Exception {
    File a =
        capture(tempDir, "a.pcapng", BSD_LOOPBACK, "lo0", List.of(packet(1, BASE.plusSeconds(1))));
    File b = capture(tempDir, "b.pcapng", ETHERNET, "en0", List.of(packet(2, BASE.plusSeconds(2))));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);
    File firstMerge = tempDir.resolve("first.pcapng").toFile();
    merger.mergeToFile(firstMerge);

    File c =
        capture(tempDir, "c.pcapng", ETHERNET, "eth0", List.of(packet(3, BASE.plusSeconds(3))));
    PcapMerger second = new PcapMerger();
    second.addRemotePcap("first", firstMerge, 0L);
    second.addRemotePcap("c", c, 0L);
    File output = tempDir.resolve("second.pcapng").toFile();
    long written = second.mergeToFile(output);

    assertThat(written).isEqualTo(3);
    List<Seen> merged = read(output);
    assertThat(merged).extracting(Seen::linkType).containsExactly(BSD_LOOPBACK, ETHERNET, ETHERNET);
    assertThat(merged).extracting(s -> s.data()[0]).containsExactly((byte) 1, (byte) 2, (byte) 3);
  }

  @Test
  @DisplayName("a packet captured by two sources is counted, and by default kept")
  void duplicatesAreCountedAndKeptByDefault(@TempDir Path tempDir) throws Exception {
    File a = capture(tempDir, "a.pcapng", ETHERNET, "lo", List.of(packet(1, BASE)));
    File b = capture(tempDir, "b.pcapng", ETHERNET, "lo", List.of(packet(1, BASE.plusMillis(1))));
    merger.addRemotePcap("local", a, 0L);
    merger.addRemotePcap("proxy", b, 0L);

    long written = merger.mergeToFile(tempDir.resolve("merged.pcapng").toFile());

    assertThat(written).isEqualTo(2);
    assertThat(merger.getDuplicatesFound()).isOne();
  }

  @Test
  @DisplayName("with dropDuplicates a packet captured by two sources is written once")
  void duplicatesCanBeDropped(@TempDir Path tempDir) throws Exception {
    File a = capture(tempDir, "a.pcapng", ETHERNET, "lo", List.of(packet(1, BASE)));
    File b = capture(tempDir, "b.pcapng", ETHERNET, "lo", List.of(packet(1, BASE.plusMillis(1))));
    merger.addRemotePcap("local", a, 0L);
    merger.addRemotePcap("proxy", b, 0L);
    merger.setDropDuplicates(true);
    File output = tempDir.resolve("merged.pcapng").toFile();

    long written = merger.mergeToFile(output);

    assertThat(written).isOne();
    assertThat(merger.getDuplicatesFound()).isOne();
    assertThat(read(output)).hasSize(1);
  }

  @Test
  @DisplayName("different packets whose hashes collide are not mistaken for duplicates")
  void hashCollisionsAreNotDuplicates(@TempDir Path tempDir) throws Exception {
    byte[] first = {0, 31};
    byte[] second = {1, 0};
    assertThat(Arrays.hashCode(first))
        .as("the premise of this test: these two have the same hash")
        .isEqualTo(Arrays.hashCode(second));
    File a =
        capture(tempDir, "a.pcapng", ETHERNET, "eth0", List.of(new Seen(null, 0, first, BASE)));
    File b =
        capture(
            tempDir,
            "b.pcapng",
            ETHERNET,
            "eth1",
            List.of(new Seen(null, 0, second, BASE.plusMillis(1))));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);
    merger.setDropDuplicates(true);

    long written = merger.mergeToFile(tempDir.resolve("merged.pcapng").toFile());

    assertThat(written).as("neither packet dropped").isEqualTo(2);
    assertThat(merger.getDuplicatesFound()).isZero();
  }

  @Test
  @DisplayName("a duplicate is found even when the source of the first copy is already used up")
  void duplicateOfAPacketFromAnExhaustedSourceIsFound(@TempDir Path tempDir) throws Exception {
    // a's only packet is long gone from memory (and its file closed) by the time b's identical
    // packet, 2 ms later, arrives: the comparison has to read it from a's file again.
    File a = capture(tempDir, "a.pcapng", ETHERNET, "eth0", List.of(packet(1, BASE)));
    File b =
        capture(
            tempDir,
            "b.pcapng",
            ETHERNET,
            "eth1",
            List.of(packet(1, BASE.plusMillis(2)), packet(2, BASE.plusMillis(3))));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);
    merger.setDropDuplicates(true);

    long written = merger.mergeToFile(tempDir.resolve("merged.pcapng").toFile());

    assertThat(written).isEqualTo(2);
    assertThat(merger.getDuplicatesFound()).isOne();
  }

  @Test
  @DisplayName("identical packets within one source are two packets, not duplicates")
  void identicalPacketsOfOneSourceAreNotDuplicates(@TempDir Path tempDir) throws Exception {
    File a =
        capture(
            tempDir,
            "a.pcapng",
            ETHERNET,
            "eth0",
            List.of(packet(1, BASE), packet(1, BASE.plusMillis(1))));
    File b = capture(tempDir, "b.pcapng", ETHERNET, "eth1", List.of(packet(2, BASE.plusMillis(2))));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);
    merger.setDropDuplicates(true);

    long written = merger.mergeToFile(tempDir.resolve("merged.pcapng").toFile());

    assertThat(written).isEqualTo(3);
    assertThat(merger.getDuplicatesFound()).isZero();
  }

  @Test
  @DisplayName("the same bytes seen well apart in time, like a retransmission, are not duplicates")
  void samePacketLongAfterIsNotADuplicate(@TempDir Path tempDir) throws Exception {
    File a = capture(tempDir, "a.pcapng", ETHERNET, "eth0", List.of(packet(1, BASE)));
    File b =
        capture(tempDir, "b.pcapng", ETHERNET, "eth1", List.of(packet(1, BASE.plusSeconds(1))));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);
    merger.setDropDuplicates(true);

    long written = merger.mergeToFile(tempDir.resolve("merged.pcapng").toFile());

    assertThat(written).isEqualTo(2);
    assertThat(merger.getDuplicatesFound()).isZero();
  }

  @Test
  @DisplayName("different packets at the same time are not duplicates")
  void differentPacketsAtTheSameTimeAreNotDuplicates(@TempDir Path tempDir) throws Exception {
    File a = capture(tempDir, "a.pcapng", ETHERNET, "eth0", List.of(packet(1, BASE)));
    File b = capture(tempDir, "b.pcapng", ETHERNET, "eth1", List.of(packet(2, BASE)));
    merger.addRemotePcap("a", a, 0L);
    merger.addRemotePcap("b", b, 0L);
    merger.setDropDuplicates(true);

    assertThat(merger.mergeToFile(tempDir.resolve("merged.pcapng").toFile())).isEqualTo(2);
    assertThat(merger.getDuplicatesFound()).isZero();
  }

  @Test
  @DisplayName("a source that is not a valid capture is skipped, the rest still merged")
  void invalidSourceIsSkipped(@TempDir Path tempDir) throws Exception {
    File garbage = tempDir.resolve("garbage.pcapng").toFile();
    Files.write(garbage.toPath(), new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14});
    File valid =
        capture(tempDir, "valid.pcapng", ETHERNET, "eth0", List.of(packet(1, BASE.plusSeconds(1))));
    merger.addRemotePcap("garbage", garbage, 0L);
    merger.addRemotePcap("valid", valid, 0L);

    File output = tempDir.resolve("merged.pcapng").toFile();
    long written = merger.mergeToFile(output);

    assertThat(written).isOne();
    assertThat(read(output)).extracting(s -> s.data()[0]).containsExactly((byte) 1);
  }
}
