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
import java.util.ArrayList;
import java.util.List;

final class PcapFiles {

  private static final int ETHERNET = 1;
  private static final int SNAPLEN = 65535;

  private PcapFiles() {}

  static byte[] frame(int marker) {
    return new byte[] {(byte) marker, 0, 0, 0};
  }

  static List<Integer> markersIn(Path file) throws IOException {
    List<Integer> markers = new ArrayList<>();
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      for (PcapNgFileReader.Packet packet = reader.next(); packet != null; packet = reader.next()) {
        markers.add((int) packet.data()[0]);
      }
    }
    return markers;
  }

  static List<String> interfaceNamesIn(Path file) throws IOException {
    List<String> names = new ArrayList<>();
    try (PcapNgFileReader reader = new PcapNgFileReader(file)) {
      for (PcapNgFileReader.Packet packet = reader.next(); packet != null; packet = reader.next()) {
        String name = reader.interfaceOf(packet.interfaceId()).name();
        if (!names.contains(name)) {
          names.add(name);
        }
      }
    }
    return names;
  }

  static Path writeCapture(Path file, String interfaceName, int marker, Instant capturedAt)
      throws IOException {
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file)) {
      int id = writer.addInterface(ETHERNET, SNAPLEN, interfaceName);
      writer.writePacket(id, frame(marker), Timestamp.from(capturedAt));
    }
    return file;
  }
}
