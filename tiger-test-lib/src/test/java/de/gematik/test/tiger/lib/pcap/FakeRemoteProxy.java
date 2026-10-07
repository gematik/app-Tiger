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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class FakeRemoteProxy implements AutoCloseable {

  private final HttpServer server;
  private final String captureId;
  private final List<String> startBodies = new CopyOnWriteArrayList<>();
  private final List<String> calls = new CopyOnWriteArrayList<>();
  private volatile byte[] capture;
  private volatile int downloadStatus = 200;
  private volatile int startStatus = 200;
  private volatile int captureCallStatus = 200;
  private volatile String captureCallError;
  private volatile CountDownLatch downloadHold;

  FakeRemoteProxy(byte[] capture) throws IOException {
    this.capture = capture;
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    captureId = "capture-" + server.getAddress().getPort();
    server.createContext("/pcap/admin/start", this::start);
    server.createContext("/pcap/admin/suspend", exchange -> answerCaptureCall(exchange, "suspend"));
    server.createContext("/pcap/admin/resume", exchange -> answerCaptureCall(exchange, "resume"));
    server.createContext("/pcap/admin/stop", this::stop);
    server.createContext("/pcap/admin/download", this::download);
    server.start();
  }

  String url() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  List<String> startBodies() {
    return startBodies;
  }

  List<String> calls() {
    return calls;
  }

  void serve(byte[] newCapture) {
    capture = newCapture;
  }

  void holdDownloadsUntil(CountDownLatch release) {
    downloadHold = release;
  }

  void refuseDownloadsWith(int status) {
    downloadStatus = status;
  }

  void refuseStartsWith(int status) {
    startStatus = status;
  }

  void refuseCaptureCallsWith(int status) {
    captureCallStatus = status;
    captureCallError = null;
  }

  void failCaptureCallsWith(int status, String error) {
    captureCallStatus = status;
    captureCallError = error;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void start(HttpExchange exchange) throws IOException {
    startBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    if (startStatus != 200) {
      respond(
          exchange,
          startStatus,
          "{\"capturing\":false,\"error\":\"refused with " + startStatus + "\"}");
      return;
    }
    respond(exchange, 200, "{\"capturing\":true,\"captureId\":\"" + captureId + "\"}");
  }

  private void stop(HttpExchange exchange) throws IOException {
    if (!isTheirCapture(exchange)) {
      return;
    }
    calls.add("stop");
    respond(
        exchange,
        200,
        "{\"capturing\":false,\"captureId\":\""
            + captureId
            + "\",\"filename\":\"capture.pcapng\",\"downloadUrl\":"
            + "\"/pcap/admin/download?captureId="
            + captureId
            + "\",\"packetCount\":1,\"fileSizeBytes\":"
            + capture.length
            + "}");
  }

  private void download(HttpExchange exchange) throws IOException {
    if (!isTheirCapture(exchange)) {
      return;
    }
    calls.add("download");
    awaitDownloadRelease();
    if (downloadStatus != 200) {
      exchange.sendResponseHeaders(downloadStatus, -1);
      exchange.close();
      return;
    }
    byte[] bytes = capture;
    exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
    exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
    if (bytes.length > 0) {
      exchange.getResponseBody().write(bytes);
    }
    exchange.close();
  }

  private void awaitDownloadRelease() {
    CountDownLatch hold = downloadHold;
    if (hold == null) {
      return;
    }
    try {
      hold.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void answerCaptureCall(HttpExchange exchange, String name) throws IOException {
    if (!isTheirCapture(exchange)) {
      return;
    }
    calls.add(name);
    respond(exchange, 200, "{\"capturing\":true,\"captureId\":\"" + captureId + "\"}");
  }

  private boolean isTheirCapture(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    boolean theirs = exchange.getRequestURI().getQuery().contains("captureId=" + captureId);
    if (!theirs) {
      calls.add("refused " + path);
      answerWithoutBody(exchange, 400);
      return false;
    }
    if (captureCallStatus != 200 && !path.endsWith("/download")) {
      answerCaptureCallFailure(exchange);
      return false;
    }
    return true;
  }

  private void answerCaptureCallFailure(HttpExchange exchange) throws IOException {
    if (captureCallError == null) {
      answerWithoutBody(exchange, captureCallStatus);
      return;
    }
    respond(exchange, captureCallStatus, "{\"error\":\"" + captureCallError + "\"}");
  }

  private static void answerWithoutBody(HttpExchange exchange, int status) throws IOException {
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  private static void respond(HttpExchange exchange, int status, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
