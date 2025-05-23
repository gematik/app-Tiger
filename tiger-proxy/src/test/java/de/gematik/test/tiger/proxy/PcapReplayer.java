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

import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import javax.net.ssl.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.TcpPacket.TcpHeader;

@Slf4j
@RequiredArgsConstructor
public class PcapReplayer implements AutoCloseable {

  private final String pcapFilename;
  private final int srcPort;
  private final int dstPort;
  private final List<TigerTestReplayPacket> toBeReplayedPackets = new ArrayList<>();
  private TigerProxy tigerProxy;
  private Socket clientSocket;

  public static PcapReplayer writeReplay(String textBlob) {
    return writeReplay(
        Arrays.stream(textBlob.split("\n"))
            .map(PcapReplayer::textLineToPacket)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList());
  }

  private static Optional<TigerTestReplayPacket> textLineToPacket(@NotNull String s) {
    if (!s.contains(":")) {
      return Optional.empty();
    }
    final String trim = s.split(":\\s*", 2)[1] + "\r\n";
    if (StringUtils.startsWithIgnoreCase(s, "client") || StringUtils.startsWithIgnoreCase(s, "c")) {
      return Optional.of(client(trim.getBytes()));
    }
    if (StringUtils.startsWithIgnoreCase(s, "server") || StringUtils.startsWithIgnoreCase(s, "s")) {
      return Optional.of(server(trim.getBytes()));
    }
    return Optional.empty();
  }

  @SneakyThrows
  public PcapReplayer readReplay() {
    final PcapHandle pcapFile = Pcaps.openOffline(pcapFilename);
    val myListener = new MyPacketListener(srcPort, dstPort, toBeReplayedPackets);
    pcapFile.setFilter("tcp", BpfCompileMode.OPTIMIZE);
    pcapFile.loop(-1, myListener);
    return this;
  }

  public static PcapReplayer writeReplay(List<TigerTestReplayPacket> toBeReplayedPackets) {
    final PcapReplayer replayer = new PcapReplayer(null, -1, -1);
    replayer.toBeReplayedPackets.addAll(toBeReplayedPackets);
    return replayer;
  }

  @SneakyThrows
  public static TigerTestReplayPacket client(String data) {
    return client(data.getBytes());
  }

  @SneakyThrows
  public static TigerTestReplayPacket server(String data) {
    return server(data.getBytes());
  }

  @SneakyThrows
  public static TigerTestReplayPacket client(byte[] data) {
    return new TigerTestReplayPacket(true, data);
  }

  @SneakyThrows
  public static TigerTestReplayPacket server(byte[] data) {
    return new TigerTestReplayPacket(false, data);
  }

  @SneakyThrows
  public TigerProxy replayWithDirectForwardUsing(TigerProxyConfiguration tigerProxyConfiguration) {
    SSLContext sslContext = createSSLContext();
    SSLServerSocketFactory serverSocketFactory = sslContext.getServerSocketFactory();
    SSLSocketFactory clientSocketFactory = sslContext.getSocketFactory();

    SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket(0);

    this.tigerProxy =
        new TigerProxy(
            tigerProxyConfiguration.setDirectReverseProxy(
                DirectReverseProxyInfo.builder()
                    .port(serverSocket.getLocalPort())
                    .hostname("localhost")
                    .build()));

    SSLSocket clientSocket =
        (SSLSocket) clientSocketFactory.createSocket("localhost", tigerProxy.getProxyPort());
    clientSocket.startHandshake();
    log.info("Replaying with direct forward using TigerProxy");
    log.info(
        "Bouncing traffic via {} -> {} and {} -> {}",
        clientSocket.getLocalPort(),
        tigerProxy.getProxyPort(),
        "<unknown>",
        serverSocket.getLocalPort());

    replayPackets(clientSocket, serverSocket);
    int before = tigerProxy.getRbelMessagesList().size();
    Thread.sleep(1000);
    int after = tigerProxy.getRbelMessagesList().size();
    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    int afterAfter = tigerProxy.getRbelMessages().size();

    log.info("Before: {}, After: {}, AfterAfter: {}", before, after, afterAfter);

    return tigerProxy;
  }

  @SneakyThrows
  private SSLContext createSSLContext() {
    val keyStore =
        new TigerPkiIdentity("src/test/resources/gateway_ecc.p12")
            .toKeyStoreWithPassword("password");
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, "password".toCharArray());

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
        keyManagerFactory.getKeyManagers(),
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(
                java.security.cert.X509Certificate[] chain, String authType) {
              // Trust all clients
            }

            @Override
            public void checkServerTrusted(
                java.security.cert.X509Certificate[] chain, String authType) {
              // Trust all servers
            }

            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return new java.security.cert.X509Certificate[0];
            }
          }
        },
        null);
    return sslContext;
  }

  @SneakyThrows
  private void replayPackets(Socket clientSocket, ServerSocket serverSocket) {
    Socket serverConnectionSocket = null;
    int bytesSendFromClientCount = 0;
    int bytesSendFromServerCount = 0;
    serverConnectionSocket = serverSocket.accept();
    for (val packet : toBeReplayedPackets) {
      if (packet.isServerRequest) {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /// CLIENT
        // ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        readNumberOfBytesFromSocket(bytesSendFromServerCount, clientSocket, "client");
        bytesSendFromServerCount = 0;
        log.atDebug()
            .addArgument(() -> new String(packet.payload).lines().findFirst().get())
            .log("Sending packet to server.... ({})");
        clientSocket.getOutputStream().write(packet.payload);
        bytesSendFromClientCount += packet.payload.length;
        clientSocket.getOutputStream().flush();
      } else {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        /// SERVER
        // ////////////////////////////////////////////////////////////////////////////////////////////////////////////
        readNumberOfBytesFromSocket(bytesSendFromClientCount, serverConnectionSocket, "server");
        bytesSendFromClientCount = 0;
        log.atDebug()
            .addArgument(() -> new String(packet.payload).lines().findFirst().get())
            .log("Sending packet to client.... ({})");
        serverConnectionSocket.getOutputStream().write(packet.payload);
        bytesSendFromServerCount += packet.payload.length;
        serverConnectionSocket.getOutputStream().flush();
      }
    }
    if (serverConnectionSocket != null) {
      serverConnectionSocket.close();
    }
  }

  @SneakyThrows
  private void readNumberOfBytesFromSocket(int bytesCount, Socket socket, String message) {
    if (bytesCount > 0) {
      log.info("Awaiting {} bytes in {} ...", bytesCount, message);
      val read = socket.getInputStream().readNBytes(bytesCount);
      log.info(
          "Read {} bytes from socket: {}",
          read.length,
          new String(read).lines().findFirst().orElse("<no data>"));
    }
  }

  @Override
  public void close() throws Exception {
    if (tigerProxy != null) {
      tigerProxy.close();
    }
    if (clientSocket != null) {
      clientSocket.close();
    }
  }

  @RequiredArgsConstructor
  private class MyPacketListener implements PacketListener {
    private final int srcPort;
    private final int dstPort;
    private final List<TigerTestReplayPacket> toBeReplayedPackets;

    @Override
    public void gotPacket(Packet packet) {
      if (packet.contains(TcpPacket.class)) {
        TcpPacket tcpPacket = packet.get(TcpPacket.class);
        TcpHeader tcpHeader = tcpPacket.getHeader();

        if (tcpHeader.getSyn() || tcpHeader.getFin() || tcpHeader.getRst()) {
          return;
        }

        if (tcpPacket.getPayload() != null && tcpPacket.getPayload().length() > 0) {
          int srcPort = tcpHeader.getSrcPort().valueAsInt();
          int dstPort = tcpHeader.getDstPort().valueAsInt();

          if (this.srcPort == srcPort && this.dstPort == dstPort
              || this.dstPort == srcPort && this.srcPort == dstPort) {
            toBeReplayedPackets.add(convertPacket(tcpPacket, srcPort));
          }
        }
      }
    }

    private TigerTestReplayPacket convertPacket(TcpPacket tcpPacket, int srcPort) {
      byte[] payload = tcpPacket.getPayload().getRawData();
      boolean isServerRequest = srcPort == this.srcPort;
      return new TigerTestReplayPacket(isServerRequest, payload);
    }
  }

  public record TigerTestReplayPacket(boolean isServerRequest, byte[] payload) {}
}
