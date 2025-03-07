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
 *
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelMessageValidatorGlueTest {

  public static final String TESTENV_CFG_LOCATION_PROPERTY =
      TigerConfigurationKeys.TIGER_TESTENV_CFGFILE_LOCATION.getKey().downsampleKey();

  @BeforeEach
  public void setUp() {
    System.setProperty(TESTENV_CFG_LOCATION_PROPERTY, "src/test/resources/minimal_tiger.yaml");
    TigerDirector.testUninitialize();
    TigerDirector.start();
  }

  @AfterEach
  public void cleanUp() {
    TigerTestEnvMgr tigerTestEnvMgr = TigerDirector.getTigerTestEnvMgr();
    if (!tigerTestEnvMgr.isShuttingDown()) {
      tigerTestEnvMgr.shutDown();
    }
    TigerDirector.testUninitialize();
    System.clearProperty(TESTENV_CFG_LOCATION_PROPERTY);
  }

  @Test
  void testSharedValidatorInstance() {
    RBelValidatorGlue glue1 = new RBelValidatorGlue();
    RBelValidatorGlue glue2 = new RBelValidatorGlue();
    Assertions.assertThat(glue1.getRbelMessageRetriever())
        .isSameAs(glue2.getRbelMessageRetriever());
    Assertions.assertThat(glue1.getRbelMessageRetriever())
        .isSameAs(RbelMessageRetriever.getInstance());
    Assertions.assertThat(RbelMessageRetriever.getInstance()).isNotNull();
  }

  @Test
  void testReadTrafficFile() {
    TigerGlobalConfiguration.putValue(
        "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersActive.yaml");
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();

          LocalProxyRbelMessageListener.getInstance().clearValidatableRbelMessages();
          RBelValidatorGlue glue = new RBelValidatorGlue();
          glue.readTgrFile("src/test/resources/testdata/rezepsFiltered.tgr");

          assertThat(LocalProxyRbelMessageListener.getInstance().getValidatableRbelMessages())
              .hasSize(96);
        });
  }

  private void executeWithSecureShutdown(Runnable test) {
    try {
      test.run();
    } finally {
      TigerDirector.getTigerTestEnvMgr().shutDown();
    }
  }
}
