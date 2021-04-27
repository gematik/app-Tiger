package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.lib.TestContext;
import de.gematik.test.tiger.lib.TestVariables;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.thucydides.core.annotations.Steps;

public class ContextGlue {

    @Steps
    TestContext ctxt;

    @Steps
    TestVariables vars;
    
    @Given("CTXT I set domain to {string}")
    public void ctxtISetDomainTo(final String domain) {
        ctxt.setDomain(domain);
    }

    @When("CTXT I set key {string} to {string}")
    public void ctxtISetKeyTo(final String key, final String value) {
        ctxt.putString(key, value);
    }

    @Then("CTXT assert key {string} matches {string}")
    public void ctxtAssertKeyMatches(final String key, final String regex) {
        ctxt.assertRegexMatches(key, regex);
    }
}
