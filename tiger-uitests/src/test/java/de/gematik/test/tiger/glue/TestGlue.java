/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.glue;

import io.cucumber.java.en.Then;

public class TestGlue {

    @Then("Wait some time")
    public void wait_some_time() {
        try {
            Thread.sleep(50000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
