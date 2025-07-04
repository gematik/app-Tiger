/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 */
package de.gematik.test.tiger.lib;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.Serenity;
import net.serenitybdd.rest.SerenityRest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class TestSerenityRestSetup {

  @BeforeEach
  void init() {
    TigerDirector.testUninitialize();
  }

  @AfterEach
  void clearProperties() {
    System.clearProperty("TIGER_TESTENV_CFGFILE");
    TigerDirector.testUninitialize();
  }

  @Test
  void trustStoreIsSet_ShouldBeValidRequestToHTTPS() {
    System.setProperty("TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/trustStoreTest.yaml");

    try {
      Serenity.throwExceptionsImmediately();
      TigerDirector.start();
      assertThat(TigerDirector.getTigerTestEnvMgr().getConfiguration().isLocalProxyActive())
          .isTrue();
      assertThat(SerenityRest.with().get("https://blub/webui").getStatusCode()).isEqualTo(200);
    } finally {
      TigerDirector.getTigerTestEnvMgr().shutDown();
    }
  }
}
