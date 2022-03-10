/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.integration.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import de.gematik.test.tiger.lib.TigerDirector;
import io.cucumber.java.en.When;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestTigerIntegrationExample {

  @When("User requests the startpage")
  public void userRequestsStartpage() {
    Unirest.config()
        .proxy("127.0.0.1", TigerDirector.getTigerTestEnvMgr().getLocalTigerProxy().getPort());
    final String url = "http://winstone";
    final HttpResponse<String> httpResponse = Unirest.get(url).asString();

    assertNotNull(httpResponse);
    assertEquals("Response code not 200", 200, httpResponse.getStatus());
  }
}
