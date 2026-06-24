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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

class CanopyDnsServerTest {

  private CanopyDnsServer server;
  private final List<CanopyDnsServer> extraServers = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    CanopyConfiguration config = new CanopyConfiguration();
    config.setDnsPort(0); // ephemeral
    config.setDefaultTtlSeconds(30);

    ApplicationEventPublisher publisher = event -> {};
    ProxiedHostRegistry registry = new ProxiedHostRegistry(publisher, config);
    registry.add("proxied.example.com", MatchType.EXACT);

    ProxyAddressProvider proxyAddresses = mock(ProxyAddressProvider.class);
    when(proxyAddresses.addressesFor(any()))
        .thenReturn(List.of(InetAddress.getByName("10.20.30.40")));

    SystemDnsResolver systemResolver = mock(SystemDnsResolver.class);
    when(systemResolver.resolve(any()))
        .thenAnswer(
            inv -> {
              Message q = inv.getArgument(0);
              Message r = new Message(q.getHeader().getID());
              r.getHeader().setRcode(Rcode.NXDOMAIN);
              if (q.getQuestion() != null) {
                r.addRecord(q.getQuestion(), Section.QUESTION);
              }
              return r;
            });

    ResolverChain chain = new ResolverChain(registry, proxyAddresses, systemResolver, config);
    DnsMessageProcessor processor = new DnsMessageProcessor(chain);
    DnsThreadPoolFactory poolFactory = new DnsThreadPoolFactory();
    server =
        new CanopyDnsServer(
            config, processor, poolFactory.udpDnsWorkerPool(), poolFactory.tcpDnsWorkerPool());
    server.start();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop();
    }
    for (CanopyDnsServer s : extraServers) {
      s.stop();
    }
    extraServers.clear();
  }

  @Test
  void registryHitReturnsProxyAddressViaUdp() throws Exception {
    SimpleResolver resolver = createResolver();
    Message query = aQuery("proxied.example.com.", Type.A);

    Message response = resolver.send(query);

    assertThat(response.getRcode()).isEqualTo(Rcode.NOERROR);
    List<Record> answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    assertThat(answers.get(0)).isInstanceOf(ARecord.class);
    assertThat(((ARecord) answers.get(0)).getAddress().getHostAddress()).isEqualTo("10.20.30.40");
  }

  @Test
  void registryMissDelegatesToSystemResolver() throws Exception {
    SimpleResolver resolver = createResolver();
    Message query = aQuery("unknown.example.com.", Type.A);

    Message response = resolver.send(query);

    assertThat(response.getRcode()).isEqualTo(Rcode.NXDOMAIN);
  }

  @Test
  void worksOverTcp() throws Exception {
    SimpleResolver resolver = createResolver();
    resolver.setTCP(true);
    Message query = aQuery("proxied.example.com.", Type.A);

    Message response = resolver.send(query);

    assertThat(response.getRcode()).isEqualTo(Rcode.NOERROR);
    assertThat(response.getSection(Section.ANSWER)).hasSize(1);
  }

  @Test
  void healthIndicatorReportsUpWhileRunningAndDownAfterStop() {
    CanopyDnsHealthIndicator indicator = new CanopyDnsHealthIndicator(server);
    assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");

    server.stop();

    assertThat(indicator.health().getStatus().getCode()).isEqualTo("DOWN");
  }

  // -------------------------------------------------------------------
  // Lifecycle / state
  // -------------------------------------------------------------------

  @Test
  void startIsIdempotentWhenAlreadyRunning() throws Exception {
    int firstPort = server.getBoundPort();
    // second invocation must short-circuit on the running flag and leave state intact
    server.start();
    assertThat(server.getBoundPort()).isEqualTo(firstPort);
    assertThat(server.isListening()).isTrue();
  }

  @Test
  void isListeningReflectsRunningStateAndStopIsIdempotent() {
    assertThat(server.isListening()).isTrue();
    server.stop();
    assertThat(server.isListening()).isFalse();
    // second stop must be a no-op
    server.stop();
    assertThat(server.isListening()).isFalse();
  }

  // -------------------------------------------------------------------
  // Wire-level error paths (UDP)
  // -------------------------------------------------------------------

  @Test
  void malformedUdpQueryReturnsFormErrPreservingId() throws Exception {
    byte[] junk = {(byte) 0xAB, (byte) 0xCD, 0x00, 0x00, 0x00, 0x00};
    byte[] respBytes = sendUdpRawAndReceive(junk, server.getBoundPort());

    Message resp = new Message(respBytes);
    assertThat(resp.getHeader().getID()).isEqualTo(0xABCD);
    assertThat(resp.getHeader().getFlag(Flags.QR)).isTrue();
    assertThat(resp.getRcode()).isEqualTo(Rcode.FORMERR);
  }

  @Test
  void resolverRuntimeExceptionMapsToServFail() throws Exception {
    ResolverChain throwingChain = mock(ResolverChain.class);
    when(throwingChain.resolve(any())).thenThrow(new RuntimeException("boom"));
    CanopyDnsServer s = freshServerWithChain(throwingChain);

    SimpleResolver resolver =
        new SimpleResolver(new InetSocketAddress("127.0.0.1", s.getBoundPort()));
    resolver.setTimeout(Duration.ofMillis(500));
    Message response = resolver.send(aQuery("any.example.com.", Type.A));

    assertThat(response.getRcode()).isEqualTo(Rcode.SERVFAIL);
    assertThat(response.getHeader().getFlag(Flags.QR)).isTrue();
    assertThat(response.getQuestion()).isNotNull();
  }

  // -------------------------------------------------------------------
  // TCP path (exercised with raw sockets to guarantee the TCP handler is hit)
  // -------------------------------------------------------------------

  @Test
  void rawTcpQueryReturnsAnswer() throws Exception {
    Message query = aQuery("proxied.example.com.", Type.A);
    Message response = exchangeOverRawTcp(query.toWire(), server.getBoundPort());

    assertThat(response.getRcode()).isEqualTo(Rcode.NOERROR);
    List<Record> answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1).first().isInstanceOf(ARecord.class);
    assertThat(((ARecord) answers.get(0)).getAddress().getHostAddress()).isEqualTo("10.20.30.40");
  }

  @Test
  void rawTcpMalformedQueryReturnsFormErr() throws Exception {
    byte[] junk = {(byte) 0xFE, (byte) 0xED, 0x00, 0x00, 0x00, 0x00};
    Message resp = exchangeOverRawTcp(junk, server.getBoundPort());

    assertThat(resp.getHeader().getID()).isEqualTo(0xFEED);
    assertThat(resp.getRcode()).isEqualTo(Rcode.FORMERR);
  }

  @Test
  void rawTcpZeroLengthPrefixIsClosedSilently() throws Exception {
    try (Socket socket = new Socket("127.0.0.1", server.getBoundPort())) {
      socket.setSoTimeout(500);
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      out.writeShort(0);
      out.flush();
      // server must close the connection without writing any bytes
      assertThat(socket.getInputStream().read()).isEqualTo(-1);
    }
  }

  // -------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------

  private CanopyDnsServer freshServerWithChain(ResolverChain chain) {
    CanopyConfiguration config = new CanopyConfiguration();
    config.setDnsPort(0);
    config.setDefaultTtlSeconds(30);
    DnsMessageProcessor processor = new DnsMessageProcessor(chain);
    DnsThreadPoolFactory poolFactory = new DnsThreadPoolFactory();
    CanopyDnsServer s =
        new CanopyDnsServer(
            config, processor, poolFactory.udpDnsWorkerPool(), poolFactory.tcpDnsWorkerPool());
    try {
      s.start();
    } catch (IOException e) {
      throw new AssertionError("Failed to start helper DNS server", e);
    }
    extraServers.add(s);
    return s;
  }

  private static byte[] sendUdpRawAndReceive(byte[] payload, int port) throws Exception {
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(500);
      InetSocketAddress dest = new InetSocketAddress("127.0.0.1", port);
      socket.send(new DatagramPacket(payload, payload.length, dest));
      byte[] buf = new byte[4096];
      DatagramPacket resp = new DatagramPacket(buf, buf.length);
      socket.receive(resp);
      byte[] out = new byte[resp.getLength()];
      System.arraycopy(resp.getData(), 0, out, 0, resp.getLength());
      return out;
    }
  }

  private static Message exchangeOverRawTcp(byte[] payload, int port) throws Exception {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.setSoTimeout(500);
      DataOutputStream out = new DataOutputStream(socket.getOutputStream());
      DataInputStream in = new DataInputStream(socket.getInputStream());
      out.writeShort(payload.length);
      out.write(payload);
      out.flush();
      int len = in.readUnsignedShort();
      byte[] respBytes = new byte[len];
      in.readFully(respBytes);
      return new Message(respBytes);
    }
  }

  private SimpleResolver createResolver() {
    SimpleResolver r =
        new SimpleResolver(new InetSocketAddress("127.0.0.1", server.getBoundPort()));
    r.setTimeout(Duration.ofMillis(500));
    return r;
  }

  private static Message aQuery(String name, int type) throws Exception {
    Record question = Record.newRecord(Name.fromString(name), type, DClass.IN);
    return Message.newQuery(question);
  }
}
