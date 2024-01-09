/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

import ch.qos.logback.classic.Level;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.stream.output.TapStream;

class TestTigerTestEnvMgrDebugLog {

  @Test
  void checkDebugOutputIsLogged_OK() throws Exception {
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TigerTestEnvMgr.class))
        .setLevel(Level.DEBUG);
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.putValue("tiger.localProxyActive", false);
    final String systemOut =
        tapSystemOut(
            () -> {
              try (TigerTestEnvMgr tigerTestEnvMgr = new TigerTestEnvMgr()) {}
            });
    assertThat(systemOut)
        .contains("TigerTestEnvMgr - Tiger configuration: {")
        .contains("TigerTestEnvMgr - Environment variables: {");
  }

  @Test
  void checkDebugOutputIsNotLoggedOnInfoLevel_OK() throws Exception {
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TigerTestEnvMgr.class))
        .setLevel(Level.INFO);
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.putValue("tiger.localProxyActive", false);
    final TapStream tapStream = new TapStream();
    try (TigerTestEnvMgr tigerTestEnvMgr = new TigerTestEnvMgr()) {
      assertThat(tapStream.getText())
          .doesNotContain("TigerTestEnvMgr - Tiger configuration: {")
          .doesNotContain("TigerTestEnvMgr - Environment variables: {");
    }
  }
}
