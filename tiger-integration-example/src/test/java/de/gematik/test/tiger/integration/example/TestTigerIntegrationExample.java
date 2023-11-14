/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.integration.example;

import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestTigerIntegrationExample {

  @Then("Hier erwarte ich einen Fehler")
  public void hier_erwarte_ich_einen_fehler() {
    // Write code here that turns the phrase above into concrete actions
    throw new io.cucumber.java.PendingException();
  }
}
