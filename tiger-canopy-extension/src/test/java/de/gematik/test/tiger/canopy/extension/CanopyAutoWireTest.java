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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.canopy.client.config.ControlMode;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import org.junit.jupiter.api.Test;

/**
 * Edge-case coverage for {@link CanopyServer#prepareDependencies()} auto-wiring beyond the
 * happy/no-proxy paths already covered in {@link CanopyServerValidationTest}.
 *
 * <p>Focus:
 *
 * <ul>
 *   <li>idempotency of repeated {@code prepareDependencies()} calls (the framework calls it once
 *       but the contract on {@code AbstractTigerServer} promises idempotency);
 *   <li>{@code dependsUpon} merging (pre-existing user entries are preserved, proxy id is
 *       appended);
 *   <li>skip-with-warning when there are multiple sibling proxies (ambiguous);
 *   <li>skip-with-warning when the unique sibling proxy lacks a usable {@code adminPort}.
 * </ul>
 */
class CanopyAutoWireTest {

  private static CfgServer cfgWith(String json) throws Exception {
    CfgServer cfg = new CfgServer().setType("canopy").setHostname("canopyunit");
    if (json != null) {
      JsonNode tree = new ObjectMapper().readTree(json);
      cfg.setTypeSpecificConfigEntry("canopy", tree);
    }
    return cfg;
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
                adminPort: 44401
                proxyPort: 44402
          """)
  void prepareDependencies_isIdempotent_acrossMultipleInvocations(TigerTestEnvMgr mgr)
      throws Exception {
    CanopyServer s = new CanopyServer("c1", cfgWith(null), mgr);

    s.prepareDependencies();
    String firstUrl = s.getCanopyConfig().getTigerProxyUrl();
    String firstDeps = s.getConfiguration().getDependsUpon();

    s.prepareDependencies();
    s.prepareDependencies();

    assertThat(s.getCanopyConfig().getTigerProxyUrl()).isEqualTo(firstUrl);
    assertThat(s.getConfiguration().getDependsUpon()).isEqualTo(firstDeps);
    assertThat(firstDeps).isEqualTo("tp"); // not "tp,tp,tp"
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
                adminPort: 44411
                proxyPort: 44412
          """)
  void singleSiblingProxy_preservesExistingDependsUpon_andAppendsProxyId(TigerTestEnvMgr mgr)
      throws Exception {
    CfgServer cfg = cfgWith(null).setDependsUpon("foo");
    CanopyServer s = new CanopyServer("c1", cfg, mgr);

    s.prepareDependencies();

    assertThat(s.getConfiguration().getDependsUpon()).isEqualTo("foo,tp");
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
                adminPort: 44421
                proxyPort: 44422
          """)
  void singleSiblingProxy_dependsUponAlreadyContainsProxy_isIdempotent(TigerTestEnvMgr mgr)
      throws Exception {
    CfgServer cfg = cfgWith(null).setDependsUpon("tp,foo");
    CanopyServer s = new CanopyServer("c1", cfg, mgr);

    s.prepareDependencies();

    // No duplicate of "tp" — original order preserved.
    assertThat(s.getConfiguration().getDependsUpon()).isEqualTo("tp,foo");
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            tp1:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44431
                proxyPort: 44432
            tp2:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 44433
                proxyPort: 44434
          """)
  void multipleProxies_skipsAutoWireAndLeavesConfigUntouched(TigerTestEnvMgr mgr) throws Exception {
    assertConfigUnchanged(mgr);
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            tpNoAdmin:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: 0
                proxyPort: 44441
          """)
  void singleSiblingProxyWithoutUsableAdminPort_skipsAutoWire(TigerTestEnvMgr mgr)
      throws Exception {
    assertConfigUnchanged(mgr);
  }

  private static void assertConfigUnchanged(TigerTestEnvMgr mgr) throws Exception {
    CanopyServer s = new CanopyServer("c1", cfgWith(null), mgr);

    s.prepareDependencies();

    assertThat(s.getCanopyConfig().getTigerProxyUrl()).isNull();
    assertThat(s.getCanopyConfig().getControlMode()).isEqualTo(ControlMode.NONE);
    assertThat(s.getConfiguration().getDependsUpon()).isNullOrEmpty();
  }
}
