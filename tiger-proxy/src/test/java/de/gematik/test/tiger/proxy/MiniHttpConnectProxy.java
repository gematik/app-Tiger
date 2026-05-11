/*
 *
 * Copyright 2021-2025 gematik GmbH
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
package de.gematik.test.tiger.proxy;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal HTTP CONNECT proxy used for tests. Accepts {@code CONNECT host:port HTTP/1.1} requests,
 * optionally validates Basic auth, opens a TCP socket to the requested target and bidirectionally
 * pumps bytes between client and target.
 */
@Slf4j
public class MiniHttpConnectProxy implements Closeable {

  @Getter private int port;
  private final String requiredUser;
  private final String requiredPass;
  private final boolean alwaysReject;
  private ServerSocket serverSocket;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  @Getter private final AtomicInteger connectRequests = new AtomicInteger();
  @Getter private final AtomicInteger acceptedTunnels = new AtomicInteger();

  /** The {@code host:port} target of the most recent CONNECT request, or {@code null}. */
  @Getter private volatile String lastConnectTarget;

  public MiniHttpConnectProxy() {
    this(0, null, null, false);
  }

  public static MiniHttpConnectProxy withBasicAuth(String user, String pass) {
    return new MiniHttpConnectProxy(0, user, pass, false);
  }

  public static MiniHttpConnectProxy alwaysReject() {
    return new MiniHttpConnectProxy(0, null, null, true);
  }

  private MiniHttpConnectProxy(int port, String user, String pass, boolean alwaysReject) {
    this.port = port;
    this.requiredUser = user;
    this.requiredPass = pass;
    this.alwaysReject = alwaysReject;
  }

  public void start() throws IOException {
    serverSocket = new ServerSocket(port);
    port = serverSocket.getLocalPort();
    executor.submit(this::acceptLoop);
  }

  private void acceptLoop() {
    while (!serverSocket.isClosed()) {
      try {
        Socket client = serverSocket.accept();
        executor.submit(() -> handleClient(client));
      } catch (IOException e) {
        if (!serverSocket.isClosed()) {
          log.debug("CONNECT proxy accept failed", e);
        }
        return;
      }
    }
  }

  private void handleClient(Socket client) {
    Socket upstream = null;
    try {
      client.setSoTimeout(5_000);
      BufferedReader reader =
          new BufferedReader(
              new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
      String requestLine = reader.readLine();
      if (requestLine == null || !requestLine.startsWith("CONNECT ")) {
        writeStatus(client, "400 Bad Request");
        return;
      }
      connectRequests.incrementAndGet();
      String[] parts = requestLine.split(" ");
      String hostPort = parts[1];
      lastConnectTarget = hostPort;
      String authHeader = null;
      String headerLine;
      while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
        if (headerLine.regionMatches(true, 0, "Proxy-Authorization:", 0, 20)) {
          authHeader = headerLine.substring(20).trim();
        }
      }

      if (alwaysReject) {
        writeStatus(client, "502 Bad Gateway");
        return;
      }
      if (requiredUser != null && !validAuth(authHeader)) {
        writeStatus(client, "407 Proxy Authentication Required");
        return;
      }

      String[] hp = hostPort.split(":");
      upstream = new Socket();
      upstream.connect(new InetSocketAddress(hp[0], Integer.parseInt(hp[1])), 5_000);
      acceptedTunnels.incrementAndGet();
      writeStatus(client, "200 Connection Established");

      pump(client, upstream);
    } catch (IOException e) {
      log.debug("CONNECT proxy session failed", e);
    } finally {
      closeQuietly(client);
      closeQuietly(upstream);
    }
  }

  private boolean validAuth(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
      return false;
    }
    String token = authHeader.substring(6).trim();
    String expected =
        Base64.getEncoder()
            .encodeToString((requiredUser + ":" + requiredPass).getBytes(StandardCharsets.UTF_8));
    return expected.equals(token);
  }

  private void writeStatus(Socket client, String status) throws IOException {
    String response = "HTTP/1.1 " + status + "\r\n\r\n";
    OutputStream out = client.getOutputStream();
    out.write(response.getBytes(StandardCharsets.US_ASCII));
    out.flush();
  }

  private void pump(Socket a, Socket b) {
    Thread t1 = new Thread(() -> copy(a, b), "proxy-pump-a-b");
    Thread t2 = new Thread(() -> copy(b, a), "proxy-pump-b-a");
    t1.setDaemon(true);
    t2.setDaemon(true);
    t1.start();
    t2.start();
    try {
      t1.join();
      t2.join();
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }

  private void copy(Socket from, Socket to) {
    try {
      InputStream in = from.getInputStream();
      OutputStream out = to.getOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) > 0) {
        out.write(buf, 0, n);
        out.flush();
      }
    } catch (IOException ignored) {
      // peer closed
    }
  }

  private static void closeQuietly(Socket s) {
    if (s == null) return;
    try {
      s.close();
    } catch (IOException ignored) {
      // ignore
    }
  }

  @Override
  public void close() {
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException ignored) {
      // ignore
    }
    executor.shutdownNow();
  }
}
