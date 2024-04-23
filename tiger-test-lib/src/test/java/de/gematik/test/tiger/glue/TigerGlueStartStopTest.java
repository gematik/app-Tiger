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

package de.gematik.test.tiger.glue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import de.gematik.test.tiger.glue.TigerGlue.TigerServerNotFoundException;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.lib.TigerLibraryException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

@TigerTest
class TigerGlueStartStopTest {

  @BeforeAll
  public static void setUp(TigerTestEnvMgr testEnvMgr) {
    ReflectionTestUtils.setField(TigerDirector.class, "tigerTestEnvMgr", testEnvMgr);
    ReflectionTestUtils.setField(TigerDirector.class, "initialized", true);
  }

  public static Stream<Arguments> startStopRunnables() {
    return Stream.of(
        Arguments.of((Consumer<String>) TigerGlue::tgrStartServer),
        Arguments.of((Consumer<String>) TigerGlue::tgrStopServer));
  }

  @ParameterizedTest
  @MethodSource("startStopRunnables")
  void startStopUnknownServer_shouldGiveException(Consumer<String> startStopRunnable) {
    assertThatThrownBy(() -> startStopRunnable.accept("unknownServer"))
        .isInstanceOf(TigerServerNotFoundException.class)
        .hasMessageContaining("Server with name unknownServer not found!");
  }

  @Test
  void startStopServerInWrongConfiguration_shouldGiveException() {
    assertThatThrownBy(() -> TigerGlue.tgrStartServer("remoteTigerProxy"))
        .isInstanceOf(TigerLibraryException.class)
        .hasMessageContaining(
            "Server with name remoteTigerProxy is not stopped! Current status is RUNNING");

    TigerGlue.tgrStopServer("remoteTigerProxy");

    assertThatThrownBy(() -> TigerGlue.tgrStopServer("remoteTigerProxy"))
        .isInstanceOf(TigerLibraryException.class)
        .hasMessageContaining(
            "Server with name remoteTigerProxy is not running! Current status is STOPPED");
  }
}
