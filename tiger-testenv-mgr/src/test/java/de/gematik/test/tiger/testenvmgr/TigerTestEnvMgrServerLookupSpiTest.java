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
package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests for the generic server-lookup SPI: {@link TigerTestEnvMgr#getServersOfType(Class)} and
 * {@link TigerTestEnvMgr#findUniqueServerOfType(Class)}. These replace canopy-named accessors that
 * earlier API drafts would have put on {@code TigerTestEnvMgr}; see {@code
 * doc/adr/canopy-extension-repo-extraction.md}.
 */
class TigerTestEnvMgrServerLookupSpiTest {

  /** Minimal in-tree server type for the test. Pretend it lives in a downstream extension. */
  static class AlphaServer extends AbstractTigerServer {
    AlphaServer(String id, TigerTestEnvMgr mgr) {
      super(id, new CfgServer().setType("alpha"), mgr);
    }

    @Override
    public void performStartup() {}

    @Override
    public void shutdown() {}
  }

  /** A second type, to assert class-discrimination works. */
  static class BetaServer extends AbstractTigerServer {
    BetaServer(String id, TigerTestEnvMgr mgr) {
      super(id, new CfgServer().setType("beta"), mgr);
    }

    @Override
    public void performStartup() {}

    @Override
    public void shutdown() {}
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void getServersOfType_returnsEmptyListWhenNoneMatch(final TigerTestEnvMgr mgr) {
    assertThat(mgr.getServersOfType(AlphaServer.class)).isEmpty();
    assertThat(mgr.findUniqueServerOfType(AlphaServer.class)).isEmpty();
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void getServersOfType_returnsAllInstancesOfThatTypeOnly(final TigerTestEnvMgr mgr) {
    AlphaServer a1 = new AlphaServer("a1", mgr);
    AlphaServer a2 = new AlphaServer("a2", mgr);
    BetaServer b1 = new BetaServer("b1", mgr);
    mgr.getServers().put("a1", a1);
    mgr.getServers().put("a2", a2);
    mgr.getServers().put("b1", b1);

    List<AlphaServer> alphas = mgr.getServersOfType(AlphaServer.class);
    assertThat(alphas).containsExactlyInAnyOrder(a1, a2);

    List<BetaServer> betas = mgr.getServersOfType(BetaServer.class);
    assertThat(betas).containsExactly(b1);
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void findUniqueServerOfType_returnsTheSoleInstance(final TigerTestEnvMgr mgr) {
    AlphaServer only = new AlphaServer("only", mgr);
    mgr.getServers().put("only", only);

    Optional<AlphaServer> found = mgr.findUniqueServerOfType(AlphaServer.class);
    assertThat(found).contains(only);
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void findUniqueServerOfType_throwsWhenMultiplePresent(final TigerTestEnvMgr mgr) {
    mgr.getServers().put("a1", new AlphaServer("a1", mgr));
    mgr.getServers().put("a2", new AlphaServer("a2", mgr));

    assertThatThrownBy(() -> mgr.findUniqueServerOfType(AlphaServer.class))
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("AlphaServer")
        .hasMessageContaining("found 2")
        .hasMessageContaining("a1")
        .hasMessageContaining("a2");
  }

  @Test
  @TigerTest(tigerYaml = "localProxyActive: false")
  void lookupMatchesBySupertype(final TigerTestEnvMgr mgr) {
    // Every concrete server is an AbstractTigerServer; lookup by the abstract base must return
    // them all (useful for "give me everything").
    mgr.getServers().put("a", new AlphaServer("a", mgr));
    mgr.getServers().put("b", new BetaServer("b", mgr));

    List<AbstractTigerServer> all = mgr.getServersOfType(AbstractTigerServer.class);
    assertThat(all).hasSize(2);
  }
}
