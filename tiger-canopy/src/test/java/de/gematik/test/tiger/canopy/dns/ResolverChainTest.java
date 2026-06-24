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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.canopy.config.CanopyConfiguration;
import de.gematik.test.tiger.canopy.registry.ProxiedHostEntry;
import de.gematik.test.tiger.canopy.registry.ProxiedHostRegistry;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

@ExtendWith(MockitoExtension.class)
class ResolverChainTest {

  @Mock private ProxyAddressProvider proxyAddresses;
  @Mock private SystemDnsResolver systemResolver;

  private CanopyConfiguration configuration;
  private ProxiedHostRegistry registry;
  private ResolverChain chain;

  @BeforeEach
  void setUp() {
    configuration = new CanopyConfiguration();
    configuration.setDefaultTtlSeconds(120);
    ApplicationEventPublisher publisher = event -> {};
    registry = new ProxiedHostRegistry(publisher, configuration);
    chain = new ResolverChain(registry, proxyAddresses, systemResolver, configuration);
  }

  private static Message buildQuery(String name, int type) throws Exception {
    Record question = Record.newRecord(Name.fromString(name + "."), type, DClass.IN);
    return Message.newQuery(question);
  }

  @Test
  void registryHitReturnsProxyAddress() throws Exception {
    InetAddress proxy = InetAddress.getByName("172.20.0.99");
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of(proxy));
    registry.add("example.com", MatchType.EXACT);

    Message response = chain.resolve(buildQuery("example.com", Type.A));

    assertThat(response.getHeader().getFlag(Flags.QR)).isTrue();
    assertThat(response.getHeader().getFlag(Flags.AA)).isTrue();
    var answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    Record firstAnswer = answers.get(0);
    assertThat(firstAnswer).isInstanceOf(ARecord.class);
    assertThat(((ARecord) firstAnswer).getAddress()).isEqualTo(proxy);
    assertThat(firstAnswer.getTTL()).isEqualTo(120L);
    verify(systemResolver, never()).resolve(any());
  }

  @Test
  void registryMissDelegatesToSystemResolver() throws Exception {
    Message query = buildQuery("not-managed.com", Type.A);
    Message upstream = new Message(query.getHeader().getID());
    when(systemResolver.resolve(query)).thenReturn(upstream);

    Message response = chain.resolve(query);

    assertThat(response).isSameAs(upstream);
    verify(systemResolver, times(1)).resolve(query);
  }

  @Test
  void aaaaQueryAgainstIpv4OnlyProxyFallsBackToIpv4() throws Exception {
    // When AAAA query finds only IPv4 addresses, fallback to provide all available families
    InetAddress v4 = InetAddress.getByName("172.20.0.99");
    assertThat(v4).isInstanceOf(Inet4Address.class);
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of(v4));
    registry.add("example.com", MatchType.EXACT);

    Message response = chain.resolve(buildQuery("example.com", Type.AAAA));

    var answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    assertThat(answers.get(0).getType()).isEqualTo(Type.A);
    assertThat(((ARecord) answers.get(0)).getAddress()).isEqualTo(v4);
    assertThat(response.getHeader().getRcode()).isZero(); // NOERROR
    verify(systemResolver, never()).resolve(any());
  }

  @Test
  void aRecordQueryAgainstIpv6OnlyProxyFallsBackToIpv6() throws Exception {
    // When A query finds only IPv6 addresses, fallback to provide all available families
    InetAddress v6 = InetAddress.getByName("2001:db8::1");
    assertThat(v6).isInstanceOf(Inet6Address.class);
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of(v6));
    registry.add("example.com", MatchType.EXACT);

    Message response = chain.resolve(buildQuery("example.com", Type.A));

    var answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    assertThat(answers.get(0).getType()).isEqualTo(Type.AAAA);
    assertThat(((AAAARecord) answers.get(0)).getAddress()).isEqualTo(v6);
    assertThat(response.getHeader().getRcode()).isZero(); // NOERROR
    verify(systemResolver, never()).resolve(any());
  }

  @Test
  void mixedAddressFamilyReturnsOnlyMatchingType() throws Exception {
    // When both IPv4 and IPv6 available and query is AAAA (matches v6),
    // return only the matching AAAA record, not the A record
    InetAddress v4 = InetAddress.getByName("172.20.0.99");
    InetAddress v6 = InetAddress.getByName("2001:db8::1");
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of(v4, v6));
    registry.add("example.com", MatchType.EXACT);

    Message response = chain.resolve(buildQuery("example.com", Type.AAAA));

    var answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    assertThat(answers.get(0).getType()).isEqualTo(Type.AAAA);
    assertThat(((AAAARecord) answers.get(0)).getAddress()).isEqualTo(v6);
  }

  @Test
  void aaaaQueryReturnsIpv6Address() throws Exception {
    InetAddress v6 = InetAddress.getByName("2001:db8::1");
    assertThat(v6).isInstanceOf(Inet6Address.class);
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of(v6));
    registry.add("example.com", MatchType.EXACT);

    Message response = chain.resolve(buildQuery("example.com", Type.AAAA));

    var answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    assertThat(answers.get(0).getType()).isEqualTo(Type.AAAA);
  }

  @Test
  void nonAddressQueryAlwaysForwarded() throws Exception {
    registry.add("example.com", MatchType.EXACT);
    Message query = buildQuery("example.com", Type.MX);
    Message upstream = new Message(query.getHeader().getID());
    when(systemResolver.resolve(query)).thenReturn(upstream);

    Message response = chain.resolve(query);

    assertThat(response).isSameAs(upstream);
    verify(proxyAddresses, never()).addressesFor(any());
  }

  @Test
  void suffixEntryMatchesSubdomain() throws Exception {
    InetAddress proxy = InetAddress.getByName("10.0.0.1");
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of(proxy));
    registry.add("example.com", MatchType.SUFFIX);

    Message response = chain.resolve(buildQuery("api.example.com", Type.A));

    var answers = response.getSection(Section.ANSWER);
    assertThat(answers).hasSize(1);
    assertThat(((ARecord) answers.get(0)).getAddress()).isEqualTo(proxy);
  }

  @Test
  void perEntryOverrideRoutesToDifferentProxyIp() throws Exception {
    InetAddress globalProxy = InetAddress.getByName("10.0.0.1");
    InetAddress pop3Proxy = InetAddress.getByName("10.0.0.7");
    registry.add("api.example.com", MatchType.EXACT);
    registry.add("pop3.example.com", MatchType.EXACT, "http://pop3-tp:9100");

    when(proxyAddresses.addressesFor(
            org.mockito.ArgumentMatchers.argThat(
                e -> e != null && "api.example.com".equals(e.getHost()))))
        .thenReturn(List.of(globalProxy));
    when(proxyAddresses.addressesFor(
            org.mockito.ArgumentMatchers.argThat(
                e -> e != null && "pop3.example.com".equals(e.getHost()))))
        .thenReturn(List.of(pop3Proxy));

    Message apiResp = chain.resolve(buildQuery("api.example.com", Type.A));
    Message pop3Resp = chain.resolve(buildQuery("pop3.example.com", Type.A));

    assertThat(((ARecord) apiResp.getSection(Section.ANSWER).get(0)).getAddress())
        .isEqualTo(globalProxy);
    assertThat(((ARecord) pop3Resp.getSection(Section.ANSWER).get(0)).getAddress())
        .isEqualTo(pop3Proxy);
  }

  @Test
  void queryWithoutQuestionReturnsFormErr() {
    Message query = new Message(4242);
    // No question section attached → ResolverChain must answer FORMERR rather than NPE.

    Message response = chain.resolve(query);

    assertThat(response.getHeader().getRcode()).isEqualTo(Rcode.FORMERR);
    assertThat(response.getHeader().getID()).isEqualTo(query.getHeader().getID());
    verify(systemResolver, never()).resolve(any());
  }

  @Test
  void registryHitWithNoMatchingAddressFamilyReturnsNoData() throws Exception {
    // Registry hit but the proxy has zero addresses available → NODATA path (no answers, no
    // NXDOMAIN, no upstream delegation).
    when(proxyAddresses.addressesFor(any(ProxiedHostEntry.class))).thenReturn(List.of());
    registry.add("example.com", MatchType.EXACT);

    Message response = chain.resolve(buildQuery("example.com", Type.A));

    assertThat(response.getSection(Section.ANSWER)).isEmpty();
    assertThat(response.getHeader().getRcode()).isEqualTo(Rcode.NOERROR);
    verify(systemResolver, never()).resolve(any());
  }
}
