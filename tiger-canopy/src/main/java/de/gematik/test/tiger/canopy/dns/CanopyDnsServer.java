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

import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Orchestrator for UDP and TCP DNS listeners. Manages lifecycle and delegates protocol handling to
 * specialized components.
 *
 * <p>Lifecycle is bound to the Spring container via {@link PostConstruct}/{@link PreDestroy}.
 * Thread pool lifecycle is managed by Spring's {@code ThreadPoolTaskExecutor}.
 */
@Slf4j
@Component
public class CanopyDnsServer {

  private final CanopyConfiguration configuration;
  private final DnsMessageProcessor processor;
  private final Executor udpDnsWorkerPool;
  private final Executor tcpDnsWorkerPool;

  private final AtomicBoolean running = new AtomicBoolean(false);

  private DatagramSocket udpSocket;
  private ServerSocket tcpSocket;
  private UdpDnsListener udpListener;
  private TcpDnsListener tcpListener;

  /** the actually bound port (useful for tests using ephemeral port 0). */
  @Getter private int boundPort;

  public CanopyDnsServer(
      CanopyConfiguration configuration,
      DnsMessageProcessor processor,
      @Qualifier("udpDnsWorkerPool") Executor udpDnsWorkerPool,
      @Qualifier("tcpDnsWorkerPool") Executor tcpDnsWorkerPool) {
    this.configuration = configuration;
    this.processor = processor;
    this.udpDnsWorkerPool = udpDnsWorkerPool;
    this.tcpDnsWorkerPool = tcpDnsWorkerPool;
  }

  @PostConstruct
  public void start() throws IOException {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    try {
      int configuredPort = configuration.getDnsPort();
      udpSocket = new DatagramSocket(new InetSocketAddress("0.0.0.0", configuredPort));
      tcpSocket = new ServerSocket();
      tcpSocket.setReuseAddress(true);
      // Bind TCP to the same port the UDP socket actually obtained (matters when port == 0).
      int udpPort = udpSocket.getLocalPort();
      tcpSocket.bind(new InetSocketAddress("0.0.0.0", udpPort));
      boundPort = udpPort;

      udpListener = new UdpDnsListener(udpSocket, udpDnsWorkerPool, processor);
      tcpListener = new TcpDnsListener(tcpSocket, tcpDnsWorkerPool, processor);

      udpListener.start();
      tcpListener.start();

      log.info("CANOPY DNS server bound to UDP/TCP port {}", boundPort);
    } catch (IOException e) {
      running.set(false);
      throw e;
    }
  }

  @PreDestroy
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    log.info("Shutting down CANOPY DNS server on port {}", boundPort);
    if (udpListener != null) {
      udpListener.stop();
    }
    if (tcpListener != null) {
      tcpListener.stop();
    }
    DnsServerUtil.closeQuietly(udpSocket);
    DnsServerUtil.closeQuietly(tcpSocket);
  }

  /**
   * @return whether both listeners are open.
   */
  public boolean isListening() {
    return running.get()
        && udpSocket != null
        && !udpSocket.isClosed()
        && tcpSocket != null
        && !tcpSocket.isClosed();
  }
}
