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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Handles TCP DNS protocol: accepts connections and dispatches processing to a worker pool. */
@Slf4j
@RequiredArgsConstructor
class TcpDnsListener {

  private final ServerSocket socket;
  private final Executor workers;
  private final DnsMessageProcessor processor;

  private Thread acceptor;

  /** Starts the TCP listener thread. */
  void start() {
    acceptor = new Thread(this::loop, "canopy-dns-tcp-acceptor");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  /** Stops the TCP listener. */
  void stop() {
    DnsServerUtil.joinQuietly(acceptor);
  }

  private void loop() {
    while (!socket.isClosed()) {
      Socket client;
      try {
        client = socket.accept();
      } catch (SocketException e) {
        log.atDebug().addArgument(e::getMessage).log("TCP socket closed: {}");
        return;
      } catch (IOException e) {
        log.atWarn().addArgument(e::getMessage).log("TCP accept failed: {}");
        continue;
      }
      try {
        workers.execute(() -> handle(client));
      } catch (RuntimeException e) {
        log.atWarn().addArgument(e::getMessage).log("TCP worker rejected connection: {}");
        DnsServerUtil.closeQuietly(client);
      }
    }
  }

  private void handle(Socket client) {
    try (Socket clientSocket = client) {
      clientSocket.setSoTimeout(5_000);
      DataInputStream in = new DataInputStream(clientSocket.getInputStream());
      DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
      int length = in.readUnsignedShort();
      if (length <= 0) {
        return;
      }
      byte[] data = new byte[length];
      in.readFully(data);
      byte[] responseBytes = processor.processQuery(data);
      if (responseBytes == null) {
        return;
      }
      out.writeShort(responseBytes.length);
      out.write(responseBytes);
      out.flush();
    } catch (IOException e) {
      log.atDebug().addArgument(e::getMessage).log("TCP client handling failed: {}");
    }
  }
}
