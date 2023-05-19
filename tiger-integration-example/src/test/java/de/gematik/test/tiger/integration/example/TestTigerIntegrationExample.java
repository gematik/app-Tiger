/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.integration.example;

import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import io.cucumber.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class TestTigerIntegrationExample {

    @Then("Hier erwarte ich einen Fehler")
    public void hier_erwarte_ich_einen_fehler() {
        // Write code here that turns the phrase above into concrete actions
        throw new io.cucumber.java.PendingException();
    }
}
