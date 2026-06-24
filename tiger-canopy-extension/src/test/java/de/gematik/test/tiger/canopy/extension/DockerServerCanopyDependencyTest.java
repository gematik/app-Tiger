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

import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.DockerServer;
import org.junit.jupiter.api.Test;

/**
 * Pre-validation tests for {@code DockerServer#doPrepareDependencies()}. The hook injects an
 * implicit {@code dependsUpon <canopyId>} edge so the canopy's {@code BeforeContainerStartEvent}
 * subscriber can resolve its dnsAddress synchronously. End-to-end coverage with a real canopy +
 * docker target lives in {@link DockerCanopyDnsIT}.
 *
 * <p>Lives in {@code tiger-canopy-extension} (not {@code tiger-testenv-mgr}) because it exercises
 * the runtime contract between {@code DockerServer} and the {@link CanopyServer}
 * {@code @TigerServerType("canopy")} token — and {@code tiger-testenv-mgr} must not depend on the
 * canopy module (enforced by {@code CanopyExtensionBoundaryTest}). Having the real canopy server on
 * the classpath here also means no stub is needed: the env-mgr resolves {@code type: canopy}
 * entries to {@link CanopyServer} the same way it does in production.
 */
class DockerServerCanopyDependencyTest {

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            myCanopy:
              type: canopy
            tgt:
              type: docker
              docker:
                image: nginx:alpine
          """)
  void singleCanopySibling_injectsDependsUpon(TigerTestEnvMgr mgr) {
    String expectedDependsOn = "myCanopy";

    assertDependsOn(mgr, expectedDependsOn);
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            myCanopy:
              type: canopy
            tgt:
              type: docker
              docker:
                image: nginx:alpine
                injectDns: false
          """)
  void injectDnsFalse_doesNotInjectDependsUpon(TigerTestEnvMgr mgr) {
    assertEmptyDependsOn(mgr);
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            myCanopy:
              type: canopy
            tgt:
              type: docker
              docker:
                image: nginx:alpine
                dnsServers:
                  - 1.1.1.1
          """)
  void explicitDnsServers_doesNotInjectDependsUpon(TigerTestEnvMgr mgr) {
    assertEmptyDependsOn(mgr);
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            canopy1:
              type: canopy
            canopy2:
              type: canopy
            tgt:
              type: docker
              docker:
                image: nginx:alpine
          """)
  void multipleCanopies_doesNotInjectDependsUpon(TigerTestEnvMgr mgr) {
    assertEmptyDependsOn(mgr);
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            tgt:
              type: docker
              docker:
                image: nginx:alpine
          """)
  void noCanopyInEnv_doesNotInjectDependsUpon(TigerTestEnvMgr mgr) {
    assertEmptyDependsOn(mgr);
  }

  @Test
  @TigerTest(
      skipEnvironmentSetup = true,
      tigerYaml =
          """
          localProxyActive: false
          servers:
            myCanopy:
              type: canopy
            tgt:
              type: docker
              dependsUpon: foo
              docker:
                image: nginx:alpine
          """)
  void existingDependsUpon_isPreservedAndCanopyIdAppended(TigerTestEnvMgr mgr) {
    assertDependsOn(mgr, "foo,myCanopy");
  }

  private static void assertEmptyDependsOn(TigerTestEnvMgr mgr) {
    var target = (DockerServer) mgr.getServers().get("tgt");

    target.prepareDependencies();

    assertThat(target.getConfiguration().getDependsUpon()).isNullOrEmpty();
  }

  private static void assertDependsOn(TigerTestEnvMgr mgr, String expectedDependsOn) {
    var target = (DockerServer) mgr.getServers().get("tgt");

    target.prepareDependencies();

    assertThat(target.getConfiguration().getDependsUpon()).isEqualTo(expectedDependsOn);
  }
}
