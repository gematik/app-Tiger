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

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PcapMerger {

  private static final Duration DUPLICATE_WINDOW = Duration.ofMillis(5);
  private static final int MAX_REMEMBERED_PACKETS = 16_384;
  private static final int MIN_DUPLICATES_TO_WARN = 3;

  private final List<PcapSource> sources = new ArrayList<>();

  @Setter private boolean dropDuplicates = false;

  @Getter private long duplicatesFound = 0;

  public void addRemotePcap(String label, File pcapFile, long clockOffsetMs) {
    log.debug("Adding pcap to merge: {} ({}) with offset: {}ms", label, pcapFile, clockOffsetMs);
    if (!pcapFile.exists()) {
      log.warn("Pcap file does not exist: {}", pcapFile);
      return;
    }
    sources.add(new PcapSource(label, pcapFile, clockOffsetMs));
  }

  public void addRemotePcap(String label, File pcapFile, java.time.Duration clockOffset) {
    addRemotePcap(label, pcapFile, clockOffset.toMillis());
  }

  public long mergeToFile(File outputFile) throws IOException {
    if (sources.isEmpty()) {
      log.warn("No pcap files to merge; creating empty file: {}", outputFile);
      if (!outputFile.exists() && !outputFile.createNewFile()) {
        log.warn("Failed to create empty output file: {}", outputFile);
      }
      return 0;
    }

    log.info("Merging {} pcap sources to {}", sources.size(), outputFile);

    PriorityQueue<PacketCursor> queue = new PriorityQueue<>();
    List<PacketCursor> cursors = new ArrayList<>();

    try {
      initializeMerger(queue, cursors);

      if (queue.isEmpty()) {
        log.warn("All sources are empty after opening; creating minimal output: {}", outputFile);
        if (!outputFile.exists() && !outputFile.createNewFile()) {
          log.warn("Failed to create minimal output file: {}", outputFile);
        }
        return 0;
      }

      return merge(queue, outputFile);
    } finally {
      cursors.forEach(PacketCursor::close);
    }
  }

  private void initializeMerger(PriorityQueue<PacketCursor> queue, List<PacketCursor> cursors) {
    for (PcapSource source : sources) {
      try {
        PacketCursor cursor =
            new PacketCursor(source.label(), source.file(), source.clockOffsetMs());
        cursors.add(cursor);
        addOrClose(cursor, queue, "Source {} has no packets", source.label());
      } catch (IOException e) {
        log.warn("Failed to open source {}: {}", source.label(), e.getMessage());
      }
    }
  }

  private static void addOrClose(
      PacketCursor cursor, PriorityQueue<PacketCursor> queue, String format, String source) {
    if (cursor.hasMore()) {
      queue.offer(cursor);
    } else {
      cursor.close();
      log.debug(format, source);
    }
  }

  private long merge(PriorityQueue<PacketCursor> queue, File outputFile) throws IOException {
    Map<PacketCursor, Map<Integer, Integer>> outputInterfaces = new IdentityHashMap<>();
    DuplicateWindow recent = new DuplicateWindow();
    duplicatesFound = 0;
    try (PcapNgFileWriter writer = new PcapNgFileWriter(outputFile.toPath())) {
      long packetCount = 0;

      while (!queue.isEmpty()) {
        PacketCursor cursor = queue.poll();
        try {
          byte[] data = cursor.currentPacket.data();
          boolean duplicate = recent.isDuplicate(cursor, data, cursor.currentTimestamp);
          if (duplicate) {
            duplicatesFound++;
          } else {
            recent.remember(
                cursor, data, cursor.currentPacket.blockOffset(), cursor.currentTimestamp);
          }
          if (!duplicate || !dropDuplicates) {
            int outputInterface = outputInterfaceFor(writer, cursor, outputInterfaces);
            writer.writePacket(outputInterface, data, cursor.currentTimestamp);
            packetCount++;
          }
          cursor.advance();
          addOrClose(cursor, queue, "Closed source {}", cursor.sourceLabel);
        } catch (IOException e) {
          cursor.close();
          throw e;
        }
      }
      reportDuplicates();
      log.debug("Merge complete: {} packets written to {}", packetCount, outputFile);
      return packetCount;
    }
  }

  private void reportDuplicates() {
    if (duplicatesFound >= MIN_DUPLICATES_TO_WARN) {
      log.warn(
          "{} packets were captured by more than one of the merged sources ({}). That happens when"
              + " the same traffic is captured twice, for example by a local capture and by a"
              + " Tiger proxy running on the same machine, or on two interfaces of one host (a"
              + " bridge and its veth)."
              + (dropDuplicates
                  ? " They were dropped from the merged file."
                  : " They are all in the merged file; set 'dropDuplicatePackets: true' to drop"
                      + " them."),
          duplicatesFound,
          sources.stream().map(PcapSource::label).toList());
    } else if (duplicatesFound > 0) {
      log.debug("{} packets were captured by more than one merged source", duplicatesFound);
    }
  }

  private static final class DuplicateWindow {
    private record Entry(
        PacketCursor source, int hash, int length, long blockOffset, Instant capturedAt) {}

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Map<Integer, List<Entry>> entriesByHash = new HashMap<>();

    boolean isDuplicate(PacketCursor source, byte[] data, Timestamp capturedAt) {
      evictOlderThanWindow(capturedAt.toInstant());
      List<Entry> candidates = entriesByHash.get(Arrays.hashCode(data));
      if (candidates == null) {
        return false;
      }
      for (Entry candidate : candidates) {
        if (candidate.source() != source
            && candidate.length() == data.length
            && hasSameBytes(candidate, data)) {
          return true;
        }
      }
      return false;
    }

    void remember(PacketCursor source, byte[] data, long blockOffset, Timestamp capturedAt) {
      Entry entry =
          new Entry(
              source, Arrays.hashCode(data), data.length, blockOffset, capturedAt.toInstant());
      entries.addLast(entry);
      entriesByHash.computeIfAbsent(entry.hash(), hash -> new ArrayList<>()).add(entry);
      while (entries.size() > MAX_REMEMBERED_PACKETS) {
        forgetOldest();
      }
    }

    private static boolean hasSameBytes(Entry remembered, byte[] data) {
      try {
        return Arrays.equals(remembered.source().reloadPacketData(remembered.blockOffset()), data);
      } catch (IOException e) {
        log.debug(
            "Could not read a packet of {} again to compare it: {}",
            remembered.source().sourceLabel,
            e.getMessage());
        return false;
      }
    }

    private void evictOlderThanWindow(Instant now) {
      Instant oldestKept = now.minus(DUPLICATE_WINDOW);
      while (!entries.isEmpty() && entries.peekFirst().capturedAt().isBefore(oldestKept)) {
        forgetOldest();
      }
    }

    private void forgetOldest() {
      Entry oldest = entries.removeFirst();
      List<Entry> sameHash = entriesByHash.get(oldest.hash());
      sameHash.removeIf(entry -> entry == oldest);
      if (sameHash.isEmpty()) {
        entriesByHash.remove(oldest.hash());
      }
    }
  }

  private static int outputInterfaceFor(
      PcapNgFileWriter writer,
      PacketCursor cursor,
      Map<PacketCursor, Map<Integer, Integer>> outputInterfaces)
      throws IOException {
    int sourceInterface = cursor.currentPacket.interfaceId();
    Map<Integer, Integer> forThisSource =
        outputInterfaces.computeIfAbsent(cursor, c -> new HashMap<>());
    Integer existing = forThisSource.get(sourceInterface);
    if (existing != null) {
      return existing;
    }

    PcapNgFileReader.Interface described = cursor.currentInterface();
    String name =
        described.name() == null
            ? cursor.sourceLabel
            : cursor.sourceLabel + " / " + described.name();
    int created = writer.addInterface(described.linkType(), described.snaplen(), name);
    forThisSource.put(sourceInterface, created);
    return created;
  }
}
