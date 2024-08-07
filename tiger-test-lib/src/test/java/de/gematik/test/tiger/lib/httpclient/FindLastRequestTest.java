/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.httpclient;

import de.gematik.test.tiger.glue.HttpGlueCode;
import de.gematik.test.tiger.glue.RBelValidatorGlue;
import de.gematik.test.tiger.glue.TigerParameterTypeDefinitions;
import de.gematik.test.tiger.lib.TigerDirector;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import io.restassured.http.Method;
import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FindLastRequestTest {
  private final HttpGlueCode httpGlueCode = new HttpGlueCode();
  private final RBelValidatorGlue rbelValidatorGlueCode = new RBelValidatorGlue();

  @BeforeAll
  public static void resetTiger() {
    TigerDirector.testUninitialize();
    TigerDirector.start();
    TigerDirector.getLibConfig().getHttpClientConfig().setActivateRbelWriter(true);
  }

  @AfterAll
  public static void shutdown() {
    TigerTestEnvMgr tigerTestEnvMgr = TigerDirector.getTigerTestEnvMgr();
    if (tigerTestEnvMgr.isShuttingDown()) {
      return;
    }
    tigerTestEnvMgr.shutDown();
    TigerDirector.testUninitialize();
  }

  /**
   * Test repeats the same requests and checks the return code, to make sure that we always find the
   * correct request also when the timing of the async parsing is slightly slower.
   */
  @TigerTest(
      tigerYaml =
          """
servers:
  httpbin:
    type: httpbin
    serverPort: ${free.port.4}
    healthcheckUrl: http://localhost:${free.port.4}/status/200
lib:
  rbelPathDebugging: false
  activateWorkflowUi: false
  trafficVisualization: true
""")
  @Test
  void testFindLastRequest() { // NOSONAR - rbelValidatorGlueCode peforms assertions.
    var amountOfRepetitions = 60;

    for (int i = 0; i < amountOfRepetitions; i++) {
      rbelValidatorGlueCode.tgrClearRecordedMessages();
      httpGlueCode.sendEmptyRequest(Method.GET, URI.create("http://httpbin/anything?foobar=1"));
      httpGlueCode.sendEmptyRequest(
          Method.GET,
          TigerParameterTypeDefinitions.tigerResolvedUrl("http://httpbin/anything?foobar=2"));
      httpGlueCode.sendEmptyRequest(
          Method.GET,
          TigerParameterTypeDefinitions.tigerResolvedUrl("http://httpbin/anything?foobar=3"));
      httpGlueCode.sendEmptyRequest(
          Method.GET,
          TigerParameterTypeDefinitions.tigerResolvedUrl("http://httpbin/status/404?other=param"));

      rbelValidatorGlueCode.findLastRequest();
      rbelValidatorGlueCode.currentResponseMessageAttributeMatches("$.responseCode", "404");
    }
  }
}
