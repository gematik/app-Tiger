/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
