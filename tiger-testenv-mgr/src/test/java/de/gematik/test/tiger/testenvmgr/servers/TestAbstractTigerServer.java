/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class TestAbstractTigerServer {

  @ParameterizedTest
  @ValueSource(strings = {"emptyList", "emptyList2", "emptyList3"})
  void testAssertCfgPropertySet_EmptyList(String propName) {
    TestServer server = new TestServer();
    TestConfigClass config = new TestConfigClass();
    assertThatThrownBy(() -> server.assertCfgPropertySet(config, propName))
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining("contain at least one non empty entry");
  }

  @ParameterizedTest
  @CsvSource(value = {"emptyStr, empty", "nullStr, NULL!"})
  void testAssertCfgPropertySet_EmptyString(String propName, String messagePart) {
    TestServer server = new TestServer();
    TestConfigClass config = new TestConfigClass();
    assertThatThrownBy(() -> server.assertCfgPropertySet(config, propName))
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining("be set and not be " + messagePart);
  }

  @Test
  void testGetServerTypeToken_NOK() {
    TestServer server = new TestServer();
    assertThatThrownBy(server::getServerTypeToken)
        .isInstanceOf(TigerTestEnvException.class)
        .hasMessageContaining(
            "has no " + TigerServerType.class.getCanonicalName() + " Annotation!");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "test_name",
        "-testName",
        "testName-",
        ".test.name",
        "test.name",
        "test..name",
        "test.name.",
        "test:name",
        "test/name"
      })
  void invalidServerNames(String serverName) {
    TestServer server = new TestServer(serverName);
    assertThatThrownBy(server::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerConfigurationException.class)
        .hasMessageContaining("Hostname '" + serverName + "' not valid (used for server 'id')");
  }

  @ParameterizedTest
  @ValueSource(strings = {"test-name", "testName"})
  void validServerNames(String serverName) {
    TestServer server = new TestServer(serverName);
    ReflectionTestUtils.setField(server, "tigerTestEnvMgr", mock(TigerTestEnvMgr.class));
    assertThatNoException().isThrownBy(server::assertThatConfigurationIsCorrect);
  }

  @Getter
  static class TestConfigClass {
    List<String> emptyList = List.of();
    List<String> emptyList2 = List.of("", "");
    List<String> emptyList3 = new ArrayList<>();

    String emptyStr = "";
    String nullStr = null;

    TestConfigClass() {
      emptyList3.add(null);
    }
  }

  static class TestServer extends AbstractTigerServer {

    public TestServer() {
      super("id", new CfgServer(), null);
    }

    public TestServer(String serverName) {
      super("id", new CfgServer().setType("testServer").setHostname(serverName), null);
    }

    @Override
    public void performStartup() {}

    @Override
    public void shutdown() {}
  }
}
