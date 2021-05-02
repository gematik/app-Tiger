package de.gematik.test.tiger.glue;

import de.gematik.test.tiger.lib.context.TestContext;
import de.gematik.test.tiger.lib.context.TestVariables;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.thucydides.core.annotations.Steps;

public class TigerGlue {

    @Steps
    TestContext ctxt;

    @Steps
    TestVariables vars;

    /**
     * defines the domain all future context actions should operate in. Variable substitution is performed.
     *
     * @param domain domain of the context. For each test suite you should use your own domain to avoid overwriting of
     *               values.
     * @see TestVariables#substituteVariables(String)
     */
    @Given("TGR I set context domain to {string}")
    public void ctxtISetContextDomainTo(final String domain) {
        ctxt.setDomain(vars.substituteVariables(domain));
    }

    /**
     * sets the context with given key to given value in the currently selected domain. Variable substitution is
     * performed.
     *
     * @param key   key of the context
     * @param value value for the context entry with given key
     * @see TestVariables#substituteVariables(String)
     */
    @When("TGR I set context entry {string} to {string}")
    public void ctxtISetContextEntryTo(final String key, final String value) {
        ctxt.putString(vars.substituteVariables(key), vars.substituteVariables(value));
    }

    /**
     * asserts that value of context entry with given key either equals or matches (regex) the given regex string.
     * Variable substitution is performed.
     * <p>
     * Special values can be used:
     * <ul>
     *     <li>$NULL ... the value of the entry should be null / JSOBObject.NULL</li>
     *     <li>$DOESNOTEXIST .. there should be no entry with given key in context of selected domain</li>
     * </ul>
     * For an in depth discussion about null/JSONObject.NULL and why we need $NULL see <a
     * href="https://stackoverflow.com/questions/13613754/how-do-you-set-a-value-to-null-with-org-json-jsonobject-in-java/13613803">this stackoverflow article</a>.
     *
     * @param key   key of entry to check
     * @param regex regular expression (or equals string) to compare the value of the entry to
     * @see TestVariables#substituteVariables(String)
     */
    @Then("TGR assert context entry {string} matches {string}")
    public void ctxtAssertContextEntryMatches(final String key, final String regex) {
        ctxt.assertRegexMatches(vars.substituteVariables(key), vars.substituteVariables(regex));
    }

    /**
     * defines the domain all future variables actions should operate in. Variable substitution is performed.
     *
     * @param domain domain of the variables. For each test suite you should use your own domain to avoid overwriting of
     *               values.
     * @see TestVariables#substituteVariables(String)
     */
    @Given("TGR I set variables domain to {string}")
    public void ctxtISetVariablesDomainTo(final String domain) {
        vars.setDomain(vars.substituteVariables(domain));
    }

    /**
     * sets the variable with given key to given value in the currently selected domain. Variable substitution is
     * performed.
     *
     * @param key   key of the context
     * @param value value for the variable with given key
     * @see TestVariables#substituteVariables(String)
     */
    @When("TGR I set variable {string} to {string}")
    public void ctxtISetVariablesEntryTo(final String key, final String value) {
        vars.putString(vars.substituteVariables(key), vars.substituteVariables(value));
    }

    /**
     * asserts that value of variable with given key either equals or matches (regex) the given regex string.
     * Variable substitution is performed.
     * <p>
     * Special values can be used:
     * <ul>
     *     <li>$NULL ... the value of the variable should be null / JSOBObject.NULL</li>
     *     <li>$DOESNOTEXIST .. there should be no entry with given key in context of selected domain</li>
     * </ul>
     * For an in depth discussion about null/JSONObject.NULL and why we need $NULL see <a
     * href="https://stackoverflow.com/questions/13613754/how-do-you-set-a-value-to-null-with-org-json-jsonobject-in-java/13613803">this stackoverflow article</a>.
     *
     * @param key   key of variable to check
     * @param regex regular expression (or equals string) to compare the value of the entry to
     * @see TestVariables#substituteVariables(String)
     */
    @Then("TGR assert variable {string} matches {string}")
    public void ctxtAssertVariablesEntryMatches(final String key, final String regex) {
        vars.assertRegexMatches(vars.substituteVariables(key), vars.substituteVariables(regex));
    }
}
