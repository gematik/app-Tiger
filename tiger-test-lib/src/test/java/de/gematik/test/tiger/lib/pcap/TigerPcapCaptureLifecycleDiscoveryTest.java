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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.PcapCaptureConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerConfigurationRoute;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibConfig;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.client.TigerRemoteProxyClient;
import de.gematik.test.tiger.proxy.data.TigerProxyRoute;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

@DisplayName("TigerPcapCaptureLifecycle: what it takes from the test environment")
class TigerPcapCaptureLifecycleDiscoveryTest {

  private final TigerPcapCaptureLifecycle lifecycle = new TigerPcapCaptureLifecycle();
  private MockedStatic<TigerDirector> director;
  private TigerTestEnvMgr environment;

  @BeforeEach
  void mockTheTestEnvironment() {
    environment = mock(TigerTestEnvMgr.class);
    director = mockStatic(TigerDirector.class);
    director.when(TigerDirector::getTigerTestEnvMgr).thenReturn(environment);
    when(environment.getServersOfType(TigerProxyServer.class)).thenReturn(List.of());
    when(environment.getLocalTigerProxyOptional()).thenReturn(Optional.empty());
  }

  @AfterEach
  void restoreTheDirector() {
    director.close();
  }

  private static TigerProxyServer serverWith(
      TigerProxyConfiguration configuration, TigerProxy bean) {
    TigerProxyServer server = mock(TigerProxyServer.class);
    when(server.getConfiguration())
        .thenReturn(new CfgServer().setTigerProxyConfiguration(configuration));
    when(server.getTigerProxyBean()).thenReturn(bean);
    when(server.getServerId()).thenReturn("server");
    return server;
  }

  private static TigerProxy runningProxy(int proxyPort, int adminPort, String... routeTargets) {
    TigerProxy proxy = mock(TigerProxy.class);
    when(proxy.getProxyPort()).thenReturn(proxyPort);
    when(proxy.getAdminPort()).thenReturn(adminPort);
    when(proxy.getRoutes())
        .thenReturn(
            java.util.Arrays.stream(routeTargets)
                .map(target -> TigerProxyRoute.builder().from("/").to(target).build())
                .toList());
    return proxy;
  }

  // ===== ports =====

  @Test
  @DisplayName("the ports are those of every proxy's routes, its own ports and the local proxy's")
  void portsAreThoseOfAllProxies() {
    TigerProxyConfiguration configured =
        new TigerProxyConfiguration()
            .setProxyRoutes(
                List.of(
                    TigerConfigurationRoute.builder().from("/a").to("http://backend:9001").build(),
                    TigerConfigurationRoute.builder().from("/b").to("https://secure").build(),
                    TigerConfigurationRoute.builder().from("/c").to("not a uri at all").build()))
            .setDirectReverseProxy(DirectReverseProxyInfo.builder().port(9002).build());
    TigerProxy bean = runningProxy(9100, 9101, "http://added-later:9103");
    TigerProxyServer server = serverWith(configured, bean);
    TigerProxy local = runningProxy(8080, 9000, "http://local-route:9104");
    when(environment.getServersOfType(TigerProxyServer.class)).thenReturn(List.of(server));
    when(environment.getLocalTigerProxyOptional()).thenReturn(Optional.of(local));

    assertThat(lifecycle.discoverProxyPorts())
        .containsExactlyInAnyOrder(9001, 443, 9002, 9100, 9101, 9103, 8080, 9000, 9104);
  }

  @Test
  @DisplayName("a server without configuration or running proxy adds nothing, and breaks nothing")
  void serverWithoutConfigurationAddsNothing() {
    TigerProxyServer bare = serverWith(null, null);
    TigerProxy runningBean = runningProxy(9100, 9101);
    TigerProxyServer ok = serverWith(new TigerProxyConfiguration(), runningBean);
    TigerProxyServer broken = mock(TigerProxyServer.class);
    when(broken.getConfiguration()).thenThrow(new IllegalStateException("not started"));
    when(broken.getServerId()).thenReturn("broken");
    when(environment.getServersOfType(TigerProxyServer.class))
        .thenReturn(List.of(bare, broken, ok));

    assertThat(lifecycle.discoverProxyPorts()).containsExactlyInAnyOrder(9100, 9101);
  }

  @Test
  @DisplayName("a proxy whose ports cannot be read adds nothing, and breaks nothing")
  void proxyWhosePortsCannotBeReadAddsNothing() {
    TigerProxy unreadable = mock(TigerProxy.class);
    when(unreadable.getProxyPort()).thenThrow(new IllegalStateException("shut down"));
    TigerProxy runningBean = runningProxy(9100, 0);
    TigerProxyServer server = serverWith(null, runningBean);
    when(environment.getLocalTigerProxyOptional()).thenReturn(Optional.of(unreadable));
    when(environment.getServersOfType(TigerProxyServer.class)).thenReturn(List.of(server));

    assertThat(lifecycle.discoverProxyPorts()).containsExactly(9100);
  }

  @Test
  @DisplayName("a test environment that cannot be asked yields no ports instead of an error")
  void unaskableEnvironmentYieldsNoPorts() {
    when(environment.getServersOfType(TigerProxyServer.class))
        .thenThrow(new IllegalStateException("no environment"));

    assertThat(lifecycle.discoverProxyPorts()).isEmpty();
  }

  @Test
  @DisplayName("the local proxy of the environment may be missing")
  void missingLocalProxyIsFine() {
    when(environment.getLocalTigerProxyOptional()).thenThrow(new IllegalStateException("none"));

    assertThat(lifecycle.discoverProxyPorts()).isEmpty();
  }

  // ===== remote proxies =====

  @Test
  @DisplayName("the remote proxies are those the local proxy is connected to, with their offsets")
  void remoteProxiesAreThoseTheLocalProxyIsConnectedTo() {
    TigerProxy local = mock(TigerProxy.class);
    TigerRemoteProxyClient first = remoteClient("http://remote-1:9000", Duration.ofMillis(120));
    TigerRemoteProxyClient second = remoteClient("http://remote-2:9000", Duration.ofSeconds(-1));
    when(local.getRemoteProxyClients()).thenReturn(List.of(first, second));
    when(environment.getLocalTigerProxyOptional()).thenReturn(Optional.of(local));

    assertThat(lifecycle.discoverRemoteProxiesWithClockOffsets())
        .isEqualTo(
            Map.of(
                "http://remote-1:9000", Duration.ofMillis(120),
                "http://remote-2:9000", Duration.ofSeconds(-1)));
  }

  @Test
  @DisplayName("no local proxy, or one that cannot be asked, means no remote proxies")
  void noLocalProxyMeansNoRemoteProxies() {
    assertThat(lifecycle.discoverRemoteProxiesWithClockOffsets()).isEmpty();

    TigerProxy local = mock(TigerProxy.class);
    TigerRemoteProxyClient twice = remoteClient("http://remote:9000", Duration.ZERO);
    when(local.getRemoteProxyClients()).thenReturn(List.of(twice, twice));
    when(environment.getLocalTigerProxyOptional()).thenReturn(Optional.of(local));
    assertThat(lifecycle.discoverRemoteProxiesWithClockOffsets())
        .as("the same proxy twice is not a reason to fail")
        .isEmpty();
  }

  private static TigerRemoteProxyClient remoteClient(String url, Duration offset) {
    TigerRemoteProxyClient client = mock(TigerRemoteProxyClient.class);
    when(client.getRemoteProxyUrl()).thenReturn(url);
    when(client.getRemoteClockOffset()).thenReturn(offset);
    return client;
  }

  // ===== the settings of a remote proxy =====

  @Test
  @DisplayName("a remote proxy gets the settings of the servers entry with its admin port")
  void remoteProxyGetsTheSettingsOfItsServersEntry() {
    PcapCaptureConfiguration wanted =
        PcapCaptureConfiguration.builder().interfaceNames(List.of("eth0")).build();
    TigerProxyServer withoutBlock =
        serverWith(new TigerProxyConfiguration().setAdminPort(9000), null);
    TigerProxyServer withBlock =
        serverWith(new TigerProxyConfiguration().setAdminPort(9001).setPcapCapture(wanted), null);
    TigerProxyServer withoutConfiguration = serverWith(null, null);
    when(environment.getServersOfType(TigerProxyServer.class))
        .thenReturn(List.of(withoutBlock, withBlock, withoutConfiguration));

    assertThat(lifecycle.resolveRemoteCaptureConfig("http://remote:9001")).isSameAs(wanted);
    assertThat(lifecycle.resolveRemoteCaptureConfig("http://remote:9000"))
        .as("its entry has no pcapCapture block")
        .isNull();
    assertThat(lifecycle.resolveRemoteCaptureConfig("http://remote:9999"))
        .as("no entry has that admin port")
        .isNull();
  }

  // ===== what the lifecycle reads from the director =====

  @Test
  @DisplayName("the capture configuration is the one of the lib configuration")
  void captureConfigurationIsTheOneOfTheLibConfiguration() {
    TigerLibConfig libConfig = new TigerLibConfig();
    TigerPcapCaptureConfig configured = new TigerPcapCaptureConfig();
    libConfig.pcapCapture = configured;
    director.when(TigerDirector::getLibConfig).thenReturn(libConfig);

    assertThat(lifecycle.loadPcapConfig()).isSameAs(configured);
  }

  @Test
  @DisplayName("the capture service is built with the configured sizes")
  void captureServiceIsBuiltWithTheConfiguredSizes() {
    TigerPcapCaptureConfig configured = new TigerPcapCaptureConfig();
    configured.setSnaplenKb(32);
    configured.setBufferSizeKb(2048);

    ScenarioPcapCaptureService service = lifecycle.createCaptureService(configured);

    assertThat(service).isNotNull();
    assertThat(service.isEnabled()).as("created, not started").isFalse();
  }

  @Test
  @DisplayName("the lifecycle listens to the test environment, and asks the director for Serenity")
  void lifecycleListensToTheEnvironmentAndAsksTheDirectorForSerenity() {
    lifecycle.registerEnvUpdateListener();
    verify(environment).registerNewListener(lifecycle);

    director.when(TigerDirector::isSerenityAvailable).thenReturn(true);
    assertThat(lifecycle.isSerenityAvailable()).isTrue();
    director.when(TigerDirector::isSerenityAvailable).thenReturn(false);
    assertThat(lifecycle.isSerenityAvailable()).isFalse();
  }
}
