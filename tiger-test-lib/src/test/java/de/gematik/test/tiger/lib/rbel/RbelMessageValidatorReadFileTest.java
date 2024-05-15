/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.rbel;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.LocalProxyRbelMessageListener;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelMessageValidatorReadFileTest {

  @BeforeEach
  @AfterEach
  public void reset() {
    TigerGlobalConfiguration.reset();
    TigerDirector.testUninitialize();
  }

  @Test
  void testReadTrafficFile() {
    TigerGlobalConfiguration.putValue(
        "TIGER_TESTENV_CFGFILE", "src/test/resources/testdata/noServersActive.yaml");
    executeWithSecureShutdown(
        () -> {
          TigerDirector.start();
          LocalProxyRbelMessageListener.clearValidatableRbelMessages();
          RbelMessageValidator.instance.readTgrFile(
              "src/test/resources/testdata/rezepsFiltered.tgr");

          assertThat(LocalProxyRbelMessageListener.getValidatableRbelMessages()).hasSize(96);
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
