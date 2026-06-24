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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import org.junit.jupiter.api.Test;

/**
 * Pre-startup validation tests for {@link CanopyServer}. End-to-end "actually boot the container"
 * lives in {@code CanopyServerStartupIT}.
 */
class CanopyServerValidationTest {

  private static CfgServer cfgWith(String json) throws Exception {
    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyunit");
    if (json != null) {
      JsonNode tree = new ObjectMapper().readTree(json);
      cfg.setTypeSpecificConfigEntry("canopy", tree);
    }
    return cfg;
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_acceptsEmptyCanopyBlock(TigerTestEnvMgr mgr) throws Exception {
    // Empty/missing block → defaults (image=null → DEFAULT_IMAGE, dnsPort=53, etc.)
    CanopyServer s = new CanopyServer("c1", cfgWith(null), mgr);

    s.prepareDependencies();
    s.assertThatConfigurationIsCorrect();

    assertThat(s.getCanopyConfig()).isNotNull();
    assertThat(s.getCanopyConfig().getDnsPort()).isEqualTo(53);
    assertThat(s.getCanopyConfig().getControlMode()).isEqualTo(ControlMode.NONE);
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_readsFullConfigViaTypedConfigSlot(TigerTestEnvMgr mgr) throws Exception {
    String json =
        """
        {
          "image": "my/tiger-canopy:1.0",
          "tigerProxyUrl": "http://tp:9090",
          "dnsPort": 5353,
          "controlMode": "ROUTE_PER_HOST"
        }
        """;
    CanopyServer s = new CanopyServer("c1", cfgWith(json), mgr);

    s.prepareDependencies();
    s.assertThatConfigurationIsCorrect();

    var cfg = s.getCanopyConfig();
    assertThat(cfg.getImage()).isEqualTo("my/tiger-canopy:1.0");
    assertThat(cfg.getTigerProxyUrl()).isEqualTo("http://tp:9090");
    assertThat(cfg.getDnsPort()).isEqualTo(5353);
    assertThat(cfg.getControlMode()).isEqualTo(ControlMode.ROUTE_PER_HOST);
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_rejectsAdminPortOutOfRange(TigerTestEnvMgr mgr) throws Exception {
    CanopyServer s = new CanopyServer("c1", cfgWith("{\"adminPort\":70000}"), mgr);

    s.prepareDependencies();
    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("adminPort")
        .hasMessageContaining("70000");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_rejectsDnsHostPortOutOfRange(TigerTestEnvMgr mgr) throws Exception {
    CanopyServer s = new CanopyServer("c1", cfgWith("{\"dnsHostPort\":-1}"), mgr);

    s.prepareDependencies();
    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("dnsHostPort");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void assertConfig_failsClearly_whenPrepareDependenciesWasNotCalled(TigerTestEnvMgr mgr)
      throws Exception {
    // Guards the new invariant: assertThat* is pure validation and must not silently initialise
    // canopyConfig. If a caller skips prepareDependencies(), we want a clear, actionable error
    // rather than an NPE-shaped one or — worse — a silent pass.
    CanopyServer s = new CanopyServer("c1", cfgWith(null), mgr);

    assertThatThrownBy(s::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("prepareDependencies");
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            tp:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44321
                proxyPort: 44322
          """)
  void autoWires_tigerProxyUrl_fromSingleSiblingTigerProxy(TigerTestEnvMgr mgr) throws Exception {
    CanopyServer s = new CanopyServer("c1", cfgWith(null), mgr);

    s.prepareDependencies();

    assertThat(s.getCanopyConfig().getTigerProxyUrl()).isEqualTo("${tp.adminUrl}");
    assertThat(s.getCanopyConfig().getControlMode()).isEqualTo(ControlMode.ROUTE_PER_HOST);
    assertThat(s.getConfiguration().getDependsUpon()).contains("tp");
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            tp:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44331
                proxyPort: 44332
          """)
  void autoWire_doesNotOverrideExplicitTigerProxyUrl(TigerTestEnvMgr mgr) throws Exception {
    CanopyServer s =
        new CanopyServer("c1", cfgWith("{\"tigerProxyUrl\":\"http://my-explicit:9999\"}"), mgr);

    s.prepareDependencies();

    assertThat(s.getCanopyConfig().getTigerProxyUrl()).isEqualTo("http://my-explicit:9999");
    // controlMode left untouched at default NONE because auto-wire never fired.
    assertThat(s.getCanopyConfig().getControlMode()).isEqualTo(ControlMode.NONE);
    // No implicit dependsUpon either — user opted out by providing the URL.
    assertThat(s.getConfiguration().getDependsUpon()).isNullOrEmpty();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void autoWire_isNoOp_whenNoSiblingTigerProxy(TigerTestEnvMgr mgr) throws Exception {
    CanopyServer s = new CanopyServer("c1", cfgWith(null), mgr);

    s.prepareDependencies();

    assertThat(s.getCanopyConfig().getTigerProxyUrl()).isNull();
    assertThat(s.getConfiguration().getDependsUpon()).isNullOrEmpty();
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            tpHttp:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44341
                proxyPort: 44342
            tpPop3:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44343
                proxyPort: 44344
            tpSmtp:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44345
                proxyPort: 44346
          """)
  void perHostOverride_addsDependsUponForEveryReferencedProxy(TigerTestEnvMgr mgr)
      throws Exception {
    // Multi-proxy setup: top-level tigerProxyUrl is set explicitly (auto-wire would skip anyway
    // because there are 3 siblings), and per-host overrides pin pop3/smtp to dedicated proxies.
    // Expected: dependsUpon picks up *every* placeholder-referenced proxy id, so canopy waits
    // for all three before booting.
    String yaml =
        """
        {
          "tigerProxyUrl": "${tpHttp.adminUrl}",
          "proxiedHosts": [
            { "host": "api.example.com" },
            { "host": "pop3.example.com", "tigerProxyUrl": "${tpPop3.adminUrl}" },
            { "host": "smtp.example.com", "tigerProxyUrl": "${tpSmtp.adminUrl}" }
          ]
        }
        """;
    CanopyServer s = new CanopyServer("c1", cfgWith(yaml), mgr);

    s.prepareDependencies();

    // dependsUpon is a CSV string; assert each referenced id appears as a token.
    String deps = s.getConfiguration().getDependsUpon();
    assertThat(deps).isNotBlank();
    assertThat(deps.split(",")).containsExactlyInAnyOrder("tpHttp", "tpPop3", "tpSmtp");
    // Top-level placeholder preserved verbatim — resolved at performStartup().
    assertThat(s.getCanopyConfig().getTigerProxyUrl()).isEqualTo("${tpHttp.adminUrl}");
    // Per-host overrides preserved verbatim too.
    assertThat(s.getCanopyConfig().getProxiedHosts())
        .extracting(TigerCanopyConfiguration.ProxiedHost::getTigerProxyUrl)
        .containsExactly(null, "${tpPop3.adminUrl}", "${tpSmtp.adminUrl}");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void perHostOverride_hardCodedUrlIsTolerated_noDependsUponAdded(TigerTestEnvMgr mgr)
      throws Exception {
    // Hard-coded URLs (no ${...} placeholder) bypass auto-wire — the user is on their own for
    // ordering. No dependsUpon edge, no warning, no crash.
    String yaml =
        """
        {
          "tigerProxyUrl": "http://default-tp:9090",
          "proxiedHosts": [
            { "host": "pop3.example.com", "tigerProxyUrl": "http://external-pop3:9100" }
          ]
        }
        """;
    CanopyServer s = new CanopyServer("c1", cfgWith(yaml), mgr);

    s.prepareDependencies();

    assertThat(s.getConfiguration().getDependsUpon()).isNullOrEmpty();
    assertThat(s.getCanopyConfig().getProxiedHosts().get(0).getTigerProxyUrl())
        .isEqualTo("http://external-pop3:9100");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void perHostOverride_selfReferenceIsSkipped(TigerTestEnvMgr mgr) throws Exception {
    // Defensive: a self-pointing placeholder would create a cycle. We deliberately drop it and
    // let any genuine cycle surface through other edges in the env mgr's cycle check.
    String yaml =
        """
        {
          "tigerProxyUrl": "http://default-tp:9090",
          "proxiedHosts": [
            { "host": "weird.example.com", "tigerProxyUrl": "${c1.adminUrl}" }
          ]
        }
        """;
    CanopyServer s = new CanopyServer("c1", cfgWith(yaml), mgr);

    s.prepareDependencies();

    // getDependsUpon() returns null when no edges were ever added — both null and "no c1
    // substring" are acceptable; the invariant is "no self-edge".
    String deps = s.getConfiguration().getDependsUpon();
    if (deps != null) {
      assertThat(deps).doesNotContain("c1");
    }
  }
}
