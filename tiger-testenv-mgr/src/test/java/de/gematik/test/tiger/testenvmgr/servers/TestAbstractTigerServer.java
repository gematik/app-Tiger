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

package de.gematik.test.tiger.testenvmgr.servers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TestAbstractTigerServer {

  @Test
  void testFindCommandInPath_OK() {
    TestServer server = new TestServer();
    if (SystemUtils.IS_OS_WINDOWS) {
      assertThat(server.findCommandInPath("cmd.exe")).isNotBlank();
    } else {
      assertThat(server.findCommandInPath("bash")).isNotBlank();
    }
  }

  @Test
  void testFindCommandInPath_NOK() {
    TestServer server = new TestServer();
    ThrowableAssert.ThrowingCallable lambda =
        () -> {
          if (SystemUtils.IS_OS_WINDOWS) {
            server.findCommandInPath("cmdNOTFOUND.exe");
          } else {
            server.findCommandInPath("bashNOTFOUND");
          }
        };
    assertThatThrownBy(lambda).isInstanceOf(TigerEnvironmentStartupException.class);
  }

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
}

class TestServer extends AbstractTigerServer {

  public TestServer() {
    super("test", "id", null, new CfgServer());
  }

  @Override
  public void performStartup() {}

  @Override
  public void shutdown() {}
}

@Getter
class TestConfigClass {
  List<String> emptyList = List.of();
  List<String> emptyList2 = List.of("", "");
  List<String> emptyList3 = new ArrayList<>();

  String emptyStr = "";
  String nullStr = null;

  TestConfigClass() {
    emptyList3.add(null);
  }
}
