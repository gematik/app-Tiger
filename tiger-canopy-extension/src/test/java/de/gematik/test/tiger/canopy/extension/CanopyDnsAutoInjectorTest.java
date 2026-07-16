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
package de.gematik.test.tiger.canopy.extension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.events.BeforeContainerStartEvent;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link CanopyDnsAutoInjector}'s decision logic. No docker daemon required; the
 * canopy's "running container" is mocked. End-to-end coverage with a real canopy + a real docker
 * server lives in {@code DockerCanopyDnsIT}.
 */
class CanopyDnsAutoInjectorTest {

  /** Helper: a CanopyServer with its {@code container} field stubbed to look "running". */
  private static CanopyServer canopyRunningAt(String ipAddress, TigerTestEnvMgr mgr) {
    CanopyServer s = new CanopyServer("canopy1", new CfgServer().setType("canopy"), mgr);
    GenericContainer<?> stub = mock(GenericContainer.class);
    when(stub.isRunning()).thenReturn(true);
    // CanopyDnsAutoInjector reads the canopy IP off the container's network settings (a host
    // label like "localhost" is rejected by docker as a DNS-server entry — see ParseAddr).
    var network = mock(com.github.dockerjava.api.model.ContainerNetwork.class);
    when(network.getIpAddress()).thenReturn(ipAddress);
    var settings = mock(com.github.dockerjava.api.model.NetworkSettings.class);
    when(settings.getNetworks()).thenReturn(java.util.Map.of("bridge", network));
    var inspect = mock(com.github.dockerjava.api.command.InspectContainerResponse.class);
    when(inspect.getNetworkSettings()).thenReturn(settings);
    when(stub.getContainerInfo()).thenReturn(inspect);
    // Reflectively set the private container field — simpler than exposing a setter.
    try {
      var f = CanopyServer.class.getDeclaredField("container");
      f.setAccessible(true);
      f.set(s, stub);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
    return s;
  }

  /** Helper: build a DockerServer with the given docker block. */
  private static DockerServer dockerServer(String id, String json, TigerTestEnvMgr mgr) {
    CfgServer cfg = new CfgServer().setType("docker").setHostname(id);
    JsonNode tree = JsonMapper.builder().build().readTree(json);
    cfg.setTypeSpecificConfigEntry("docker", tree);
    DockerServer s = new DockerServer(id, cfg, mgr);
    s.assertThatConfigurationIsCorrect();
    return s;
  }

  private static BeforeContainerStartEvent event(
      AbstractTigerServer server, List<String> dns, Map<String, String> env) {
    return new BeforeContainerStartEvent(server, new ArrayList<>(dns), new ArrayList<>(), env);
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void injectsCanopyDnsWhenCarrierIsEmpty(TigerTestEnvMgr mgr) {
    CanopyServer canopy = canopyRunningAt("10.0.0.5", mgr);
    mgr.getServers().put("canopy1", canopy);
    DockerServer docker = dockerServer("app", "{\"image\":\"nginx\"}", mgr);
    new CanopyDnsAutoInjector(mgr, canopy).register();

    var evt = event(docker, List.of(), new HashMap<>());
    mgr.getLifecycleEventBus().publish(evt);

    assertThat(evt.getDnsServers()).containsExactly("10.0.0.5");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void skipsWhenCarrierAlreadyHasExplicitDns(TigerTestEnvMgr mgr) {
    CanopyServer canopy = canopyRunningAt("10.0.0.5", mgr);
    mgr.getServers().put("canopy1", canopy);
    DockerServer docker = dockerServer("app", "{\"image\":\"nginx\"}", mgr);
    new CanopyDnsAutoInjector(mgr, canopy).register();

    var evt = event(docker, List.of("1.1.1.1"), new HashMap<>());
    mgr.getLifecycleEventBus().publish(evt);

    assertThat(evt.getDnsServers()).containsExactly("1.1.1.1"); // unchanged
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void skipsWhenInjectDnsFalse(TigerTestEnvMgr mgr) {
    CanopyServer canopy = canopyRunningAt("10.0.0.5", mgr);
    mgr.getServers().put("canopy1", canopy);
    DockerServer docker = dockerServer("app", "{\"image\":\"nginx\",\"injectDns\":false}", mgr);
    new CanopyDnsAutoInjector(mgr, canopy).register();

    var evt = event(docker, List.of(), new HashMap<>());
    mgr.getLifecycleEventBus().publish(evt);

    assertThat(evt.getDnsServers()).isEmpty();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void skipsWhenTargetIsItselfACanopy(TigerTestEnvMgr mgr) {
    CanopyServer canopy = canopyRunningAt("10.0.0.5", mgr);
    mgr.getServers().put("canopy1", canopy);
    CanopyServer otherCanopy = new CanopyServer("canopy2", new CfgServer().setType("canopy"), mgr);
    new CanopyDnsAutoInjector(mgr, canopy).register();

    var evt = event(otherCanopy, List.of(), new HashMap<>());
    mgr.getLifecycleEventBus().publish(evt);

    assertThat(evt.getDnsServers()).isEmpty();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void skipsWhenMultipleCanopiesPresent(TigerTestEnvMgr mgr) {
    CanopyServer c1 = canopyRunningAt("10.0.0.5", mgr);
    CanopyServer c2 = canopyRunningAt("10.0.0.6", mgr);
    mgr.getServers().put("canopy1", c1);
    mgr.getServers().put("canopy2", c2);
    DockerServer docker = dockerServer("app", "{\"image\":\"nginx\"}", mgr);
    new CanopyDnsAutoInjector(mgr, c1).register();

    var evt = event(docker, List.of(), new HashMap<>());
    mgr.getLifecycleEventBus().publish(evt);

    assertThat(evt.getDnsServers()).isEmpty();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void skipsWhenCanopyContainerNotYetRunning(TigerTestEnvMgr mgr) {
    CanopyServer canopy =
        new CanopyServer("canopy1", new CfgServer().setType("canopy"), mgr); // no container
    mgr.getServers().put("canopy1", canopy);
    DockerServer docker = dockerServer("app", "{\"image\":\"nginx\"}", mgr);
    new CanopyDnsAutoInjector(mgr, canopy).register();

    var evt = event(docker, List.of(), new HashMap<>());
    mgr.getLifecycleEventBus().publish(evt);

    assertThat(evt.getDnsServers()).isEmpty();
  }
}
