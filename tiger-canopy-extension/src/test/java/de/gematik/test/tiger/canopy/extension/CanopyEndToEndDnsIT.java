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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.gematik.test.tiger.canopy.client.config.MatchType;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;

/**
 * Closes the loop on the auto-DNS handoff by running an actual {@code nslookup} from inside the
 * target docker container and asserting the resolver path lands on the canopy.
 *
 * <p>{@link DockerCanopyDnsIT} verifies the {@code --dns} entry is injected into the container
 * builder; this test verifies the container <em>actually uses</em> that DNS server at runtime and
 * that canopy <em>actually answers</em> queries for a seeded proxied host.
 *
 * <p>Test scenario:
 *
 * <ul>
 *   <li>Boot canopy (with a deliberately-fake but IP-parseable {@code tigerProxyUrl} so canopy's
 *       proxied-host responses are deterministic).
 *   <li>Boot an alpine target — the auto-DNS injector wires canopy's IP into the container's {@code
 *       --dns} list.
 *   <li>Seed canopy with {@code api.example.com → EXACT} via {@link
 *       de.gematik.test.tiger.canopy.client.CanopyAdminClient}.
 *   <li>{@code exec nslookup api.example.com} inside the alpine target.
 *   <li>Assert: nslookup's "Server" line points at the canopy IP (so the lookup really went through
 *       canopy) and the answer contains the fake proxy IP.
 * </ul>
 *
 * <p>Auto-skipped when Docker is not available or no canopy image can be resolved.
 */
class CanopyEndToEndDnsIT {

  // A parseable but unroutable IPv4 inside RFC 5737 TEST-NET-2 — canopy publishes this back as
  // the answer for proxied hosts (it parses the host out of tigerProxyUrl). Using a literal IP
  // means we don't need a real tiger-proxy in this IT.
  private static final String FAKE_PROXY_IP = "198.51.100.42";

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void nslookupInsideTargetGoesThroughCanopyAndReturnsProxyIp(TigerTestEnvMgr mgr)
      throws Exception {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker daemon not available");
    Optional<String> resolved = CanopyItImageResolver.resolve();
    assumeTrue(
        resolved.isPresent(),
        "No canopy image available — set -D"
            + CanopyItImageResolver.PROPERTY
            + " or pre-load one of: "
            + CanopyItImageResolver.CANDIDATE_TAGS);
    String canopyImage = resolved.get();

    String canopyYaml =
        """
        image: %s
        # controlMode=NONE keeps the registry purely local — no outbound bridge call to the
        # (fake, unroutable) tigerProxyUrl on every add(). DNS rewriting itself is independent
        # of controlMode: canopy still parses tigerProxyUrl's host into the IP it answers for
        # proxied hosts.
        controlMode: NONE
        tigerProxyUrl: http://%s:8080
        """
            .formatted(canopyImage, FAKE_PROXY_IP);
    JsonNode canopyBlock = new YAMLMapper().readTree(canopyYaml);
    CfgServer canopyCfg = new CfgServer().setType("canopy").setHostname("canopye2e");
    canopyCfg.setTypeSpecificConfigEntry("canopy", canopyBlock);

    // Alpine 3.20 ships busybox `nslookup`. Keep the container alive with a sleep loop.
    String dockerYaml =
        """
        image: alpine:3.20
        exposedPorts: []
        command: ["sh", "-c", "while true; do sleep 1; done"]
        waitStrategy:
          kind: NONE
          timeoutSeconds: 30
        """;
    JsonNode dockerBlock = new YAMLMapper().readTree(dockerYaml);
    CfgServer dockerCfg = new CfgServer().setType("docker").setHostname("e2etarget");
    dockerCfg.setTypeSpecificConfigEntry("docker", dockerBlock);

    CanopyServer canopy = new CanopyServer("canopye2e", canopyCfg, mgr);
    DockerServer target = new DockerServer("e2etarget", dockerCfg, mgr);
    mgr.getServers().put("canopye2e", canopy);
    mgr.getServers().put("e2etarget", target);

    try {
      canopy.prepareDependencies();
      target.prepareDependencies();
      canopy.assertThatConfigurationIsCorrect();
      canopy.performStartup();
      target.assertThatConfigurationIsCorrect();
      target.performStartup();

      // Seed canopy with one proxied host.
      canopy.getAdminClient().add("api.example.com", MatchType.EXACT);

      String canopyIp = canopy.getContainerNetworkIp();
      assertThat(canopyIp)
          .as("canopy must have a network IP visible from sibling containers")
          .isNotNull()
          .matches("\\d+\\.\\d+\\.\\d+\\.\\d+");

      // Run nslookup inside the alpine target. Busybox output looks like:
      //   Server:    <dns-server-ip>
      //   Address 1: <dns-server-ip>
      //   Name:      api.example.com
      //   Address 1: <answer-ip>
      Container.ExecResult proxied =
          target.getDockerContainer().execInContainer("nslookup", "api.example.com");
      String proxiedOut = proxied.getStdout() + proxied.getStderr();

      assertThat(proxiedOut)
          .as(
              "nslookup must show the canopy IP as the resolver — proves DNS routing went through canopy")
          .contains(canopyIp);
      assertThat(proxiedOut)
          .as("canopy must return the configured proxy IP for a seeded proxied host")
          .contains(FAKE_PROXY_IP);

      // Sanity: an unseeded host still hits canopy (Server line) — canopy's behaviour for
      // unproxied hosts is canopy's own concern, so we only verify the Server line here.
      Container.ExecResult unproxied =
          target.getDockerContainer().execInContainer("nslookup", "not.seeded.example");
      String unproxiedOut = unproxied.getStdout() + unproxied.getStderr();
      assertThat(unproxiedOut)
          .as("unseeded lookup must still hit canopy as the resolver")
          .contains(canopyIp);
    } finally {
      try {
        target.shutdown();
      } finally {
        canopy.shutdown();
        mgr.getServers().remove("e2etarget");
        mgr.getServers().remove("canopye2e");
      }
    }
  }
}
