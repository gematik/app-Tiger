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
 *
 */
package de.gematik.test.tiger.canopy.dns;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Handles UDP DNS protocol: receives packets and dispatches processing to a worker pool. */
@Slf4j
@RequiredArgsConstructor
class UdpDnsListener {

  private static final int UDP_BUFFER_SIZE = 4096;

  private final DatagramSocket socket;
  private final Executor workers;
  private final DnsMessageProcessor processor;

  private Thread receiver;

  /** Starts the UDP listener thread. */
  void start() {
    receiver = new Thread(this::loop, "canopy-dns-udp-receiver");
    receiver.setDaemon(true);
    receiver.start();
  }

  /** Stops the UDP listener. */
  void stop() {
    DnsServerUtil.joinQuietly(receiver);
  }

  private void loop() {
    while (!socket.isClosed()) {
      byte[] buf = new byte[UDP_BUFFER_SIZE];
      DatagramPacket packet = new DatagramPacket(buf, buf.length);
      try {
        socket.receive(packet);
      } catch (SocketException e) {
        log.atDebug().addArgument(e::getMessage).log("UDP socket closed: {}");
        return;
      } catch (IOException e) {
        log.atWarn().addArgument(e::getMessage).log("UDP receive failed: {}");
        continue;
      }
      final byte[] data = new byte[packet.getLength()];
      System.arraycopy(packet.getData(), 0, data, 0, data.length);
      final InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
      try {
        workers.execute(() -> handle(data, sender));
      } catch (RuntimeException e) {
        log.atWarn()
            .addArgument(sender)
            .addArgument(e::getMessage)
            .log("UDP worker rejected query from {}: {}");
      }
    }
  }

  private void handle(byte[] data, InetSocketAddress sender) {
    byte[] responseBytes = processor.processQuery(data);
    if (responseBytes == null) {
      return;
    }
    try {
      socket.send(new DatagramPacket(responseBytes, responseBytes.length, sender));
    } catch (IOException e) {
      log.atDebug()
          .addArgument(sender)
          .addArgument(e::getMessage)
          .log("Failed to send UDP response to {}: {}");
    }
  }
}
