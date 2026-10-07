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

import com.sun.net.httpserver.HttpServer;
import de.gematik.test.tiger.proxy.handler.pcap.PcapMerger;
import de.gematik.test.tiger.proxy.handler.pcap.PcapNgFileReader;
import de.gematik.test.tiger.proxy.handler.pcap.PcapNgFileWriter;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kong.unirest.core.Unirest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Remote PCAP Capture Integration")
class RemotePcapCaptureIntegrationTest {

  private static final Instant BASE = Instant.ofEpochSecond(1_700_000_000L);

  private static HttpServer fakeRemoteProxy(File capture, Map<String, String> startBodies)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    String name = "proxy-" + server.getAddress().getPort();
    server.createContext(
        "/pcap/admin/start",
        exchange -> {
          startBodies.put(name, new String(exchange.getRequestBody().readAllBytes()));
          respond(exchange, "{\"capturing\":true,\"captureId\":\"" + name + "\"}");
        });
    server.createContext(
        "/pcap/admin/stop",
        exchange -> {
          respond(
              exchange,
              "{\"capturing\":false,\"captureId\":\""
                  + name
                  + "\",\"filename\":\"capture.pcapng\",\"downloadUrl\":"
                  + "\"/pcap/admin/download?captureId="
                  + name
                  + "\",\"packetCount\":1,\"fileSizeBytes\":"
                  + capture.length()
                  + "}");
        });
    server.createContext(
        "/pcap/admin/download",
        exchange -> {
          byte[] bytes = Files.readAllBytes(capture.toPath());
          exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return server;
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json)
      throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static File captureWithOnePacket(Path dir, String name, int marker, Instant at)
      throws IOException {
    File file = dir.resolve(name).toFile();
    try (PcapNgFileWriter writer = new PcapNgFileWriter(file.toPath())) {
      int id = writer.addInterface(1, 65535, "eth0");
      writer.writePacket(id, new byte[] {(byte) marker}, Timestamp.from(at));
    }
    return file;
  }

  @Test
  @DisplayName("start, stop, download and clock-corrected merge across two remote proxies")
  void testDistributedCaptureWorkflow(@TempDir Path tempDir) throws Exception {
    // proxy 1 is in sync; proxy 2's clock runs one second ahead, so what it stamped at +2.0 s
    // really happened at +1.0 s, i.e. before proxy 1's packet at +1.5 s.
    File capture1 = captureWithOnePacket(tempDir, "remote1.pcapng", 1, BASE.plusMillis(1500));
    File capture2 = captureWithOnePacket(tempDir, "remote2.pcapng", 2, BASE.plusMillis(2000));
    Map<String, String> startBodies = new ConcurrentHashMap<>();
    HttpServer proxy1 = fakeRemoteProxy(capture1, startBodies);
    HttpServer proxy2 = fakeRemoteProxy(capture2, startBodies);
    RemotePcapCoordinator coordinator = new RemotePcapCoordinator();
    try {
      String url1 = "http://localhost:" + proxy1.getAddress().getPort();
      String url2 = "http://localhost:" + proxy2.getAddress().getPort();

      coordinator.startRemoteCapture(
          Map.of(url1, Duration.ZERO, url2, Duration.ofSeconds(1)), "integration-test");
      Map<String, RemotePcapMetadata> metadata = coordinator.stopRemoteCapture();

      assertThat(metadata).containsOnlyKeys(url1, url2);
      assertThat(startBodies.values())
          .as("both proxies were told which suite is asking, so gap captures stay per suite")
          .hasSize(2)
          .allSatisfy(body -> assertThat(body).contains("\"suiteId\":\""));
      assertThat(suiteIdIn(startBodies.get("proxy-" + proxy1.getAddress().getPort())))
          .isEqualTo(suiteIdIn(startBodies.get("proxy-" + proxy2.getAddress().getPort())));

      PcapMerger merger = new PcapMerger();
      for (Map.Entry<String, RemotePcapMetadata> entry : metadata.entrySet()) {
        File downloaded =
            tempDir.resolve("downloaded-" + entry.getKey().replaceAll("[^a-z0-9]", "_")).toFile();
        Unirest.get(entry.getKey() + entry.getValue().getDownloadUrl())
            .asFile(downloaded.getAbsolutePath());
        merger.addRemotePcap(entry.getKey(), downloaded, entry.getValue().getClockOffset());
      }
      File merged = tempDir.resolve("merged.pcapng").toFile();
      long written = merger.mergeToFile(merged);

      assertThat(written).isEqualTo(2);
      List<Byte> order = new ArrayList<>();
      try (PcapNgFileReader reader = new PcapNgFileReader(merged.toPath())) {
        for (PcapNgFileReader.Packet packet = reader.next();
            packet != null;
            packet = reader.next()) {
          order.add(packet.data()[0]);
        }
      }
      assertThat(order)
          .as("proxy 2's packet, once its clock offset is taken off, comes first")
          .containsExactly((byte) 2, (byte) 1);
    } finally {
      proxy1.stop(0);
      proxy2.stop(0);
      coordinator.clear();
    }
  }

  private static String suiteIdIn(String startRequestBody) {
    int start = startRequestBody.indexOf("\"suiteId\":\"") + "\"suiteId\":\"".length();
    return startRequestBody.substring(start, startRequestBody.indexOf('"', start));
  }

  @Test
  @DisplayName("the proxies of one scenario are called in parallel, not one after the other")
  void proxiesAreCalledInParallel(@TempDir Path tempDir) throws Exception {
    List<HttpServer> slowProxies = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      HttpServer slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      slow.createContext(
          "/pcap/admin/start",
          exchange -> {
            try {
              Thread.sleep(1000); // each proxy takes a second to answer
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            respond(exchange, "{\"capturing\":true,\"captureId\":\"s\"}");
          });
      slow.start();
      slowProxies.add(slow);
    }
    RemotePcapCoordinator coordinator = new RemotePcapCoordinator();
    try {
      Map<String, Duration> proxies = new java.util.HashMap<>();
      slowProxies.forEach(
          s -> proxies.put("http://localhost:" + s.getAddress().getPort(), Duration.ZERO));

      long began = System.nanoTime();
      coordinator.startRemoteCapture(proxies, "parallel");
      long tookMillis = Duration.ofNanos(System.nanoTime() - began).toMillis();

      assertThat(tookMillis)
          .as("three proxies of a second each, called one after the other, would take 3 s")
          .isLessThan(2500);
    } finally {
      slowProxies.forEach(s -> s.stop(0));
      coordinator.clear();
    }
  }

  @Test
  @DisplayName("a remote proxy that refuses to be captured (403) is reported, not hung on")
  void refusingProxyIsNotWaitedFor(@TempDir Path tempDir) throws Exception {
    HttpServer refusing = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    refusing.createContext(
        "/pcap/admin/start",
        exchange -> {
          exchange.sendResponseHeaders(403, -1);
          exchange.close();
        });
    refusing.start();
    RemotePcapCoordinator coordinator = new RemotePcapCoordinator();
    try {
      String url = "http://localhost:" + refusing.getAddress().getPort();

      coordinator.startRemoteCapture(Map.of(url, Duration.ZERO), "refused");

      assertThat(coordinator.stopRemoteCapture())
          .as("nothing was started on the proxy, so there is nothing to stop or download")
          .isEmpty();
    } finally {
      refusing.stop(0);
      coordinator.clear();
    }
  }

  @Test
  @DisplayName("a remote proxy that stops answering is given up on after the request timeout")
  void silentProxyIsGivenUpOn(@TempDir Path tempDir) throws Exception {
    HttpServer silent = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    silent.createContext(
        "/pcap/admin/start",
        exchange -> {
          try {
            Thread.sleep(5000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    silent.start();
    RemotePcapCoordinator coordinator = new RemotePcapCoordinator();
    coordinator.setRequestTimeout(Duration.ofMillis(500));
    try {
      String url = "http://localhost:" + silent.getAddress().getPort();

      long began = System.nanoTime();
      coordinator.startRemoteCapture(Map.of(url, Duration.ZERO), "silent");
      long tookMillis = Duration.ofNanos(System.nanoTime() - began).toMillis();

      assertThat(tookMillis).as("must not wait for the proxy's 5 s answer").isLessThan(3000);
      assertThat(coordinator.stopRemoteCapture()).isEmpty();
    } finally {
      silent.stop(0);
      coordinator.clear();
    }
  }
}
