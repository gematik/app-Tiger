package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.common.TokenSubstituteHelper;
import de.gematik.test.tiger.common.context.TestContext;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.lib.rbel.RbelMessageValidator;
import io.cucumber.java.de.Dann;
import io.cucumber.java.de.Gegebensei;
import io.cucumber.java.de.Wenn;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.regex.Pattern;
import org.xmlunit.builder.DiffBuilder;

public class RBelValidatorGlue {

    static RbelMessageValidator rbelValidator = new RbelMessageValidator();

    public static RbelMessageValidator getRbelValidator() {
        return rbelValidator;
    }

    // =================================================================================================================
    //
    //    R E Q U E S T   F I L T E R I N G
    //
    // =================================================================================================================

    /**
     * Specify the amount of seconds Tiger should wait when filtering for requests / responses before reporting them as not found.
     */
    @Gegebensei("TGR setze Anfrage Timeout auf {int} Sekunden")
    @Given("TGR set request wait timeout to {int} seconds")
    public void tgrSetRequestWaitTimeout(final int waitsec) {
        rbelValidator.getContext().getContext("tiger").put("rbel.request.timeout", waitsec);
    }

    /**
     * clear all validatable rbel messages. This does not clear the recorded messages later on reported via the rbel log HTML page or the
     * messages shown on web ui of tiger proxies.
     */
    @Wenn("TGR lösche aufgezeichnete Nachrichten")
    @When("TGR clear recorded messages")
    public void tgrClearRecordedMessages() {
        rbelValidator.clearRBelMessages();
    }


    /**
     * filter all subsequent findRequest steps for hostname. To reset set host name to empty string "".
     *
     * @param hostname host name (regex supported) to filter for
     */
    @Wenn("TGR filtere Anfragen nach Server {string}")
    @When("TGR filter requests based on host {string}")
    public void tgrFilterBasedOnHost(final String hostname) {
        rbelValidator.getContext().getContext("tiger").put("rbel.request.filter.host", hostname);
    }

    /**
     * filter all subsequent findRequest steps for method.
     *
     * @param method method to filter for
     */
    @Wenn("TGR filtere Anfragen nach HTTP Methode {string}")
    @When("TGR filter requests based on method {string}")
    public void tgrFilterBasedOnMethod(final String method) {
        rbelValidator.getContext().getContext("tiger").put("rbel.request.filter.method", method.toUpperCase());
    }

    /**
     * reset filter for method for subsequent findRequest steps.
     */
    @Wenn("TGR lösche den gesetzten HTTP Methodenfilter")
    @When("TGR reset request method filter")
    public void tgrResetRequestMethodFilter() {
        rbelValidator.getContext().getContext("tiger").put("rbel.request.filter.method", null);
    }

    /**
     * find the first request where the path equals or matches as regex and memorize it in the {@link #rbelValidator} instance
     *
     * @param path path to match
     */
    @Wenn("TGR finde die erste Anfrage mit Pfad {string}")
    @When("TGR find request to path {string}")
    public void findRequestToPath(final String path) {
        final String parsedPath = TokenSubstituteHelper.substitute(path, "", new TestContext().getContext("tiger"));
        rbelValidator.filterRequestsAndStoreInContext(parsedPath, null, null, false);
    }

    /**
     * find the first request where path and node value equal or match as regex and memorize it in the {@link #rbelValidator} instance.
     *
     * @param path     path to match
     * @param rbelPath rbel path to node/attribute
     * @param value    value to match at given node/attribute
     */
    @Wenn("TGR finde die erste Anfrage mit Pfad {string} und Knoten {string} der mit {string} übereinstimmt")
    @When("TGR find request to path {string} with {string} matching {string}")
    public void findRequestToPathWithCommand(final String path, final String rbelPath, final String value) {
        final String parsedPath = TokenSubstituteHelper.substitute(path, "", new TestContext().getContext("tiger"));
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String parsedValue = TokenSubstituteHelper.substitute(value, "", new TestContext().getContext("tiger"));
        rbelValidator.filterRequestsAndStoreInContext(parsedPath, parsedRbelPath, parsedValue, false);
    }

    /**
     * find the NEXT request where the path equals or matches as regex and memorize it in the {@link #rbelValidator} instance.
     *
     * @param path path to match
     */
    @Wenn("TGR finde die nächste Anfrage mit dem Pfad {string}")
    @When("TGR find next request to path {string}")
    public void findNextRequestToPath(final String path) {
        final String parsedPath = TokenSubstituteHelper.substitute(path, "", new TestContext().getContext("tiger"));
        rbelValidator.filterRequestsAndStoreInContext(parsedPath, null, null, true);
    }

    /**
     * find the NEXT request where path and node value equal or match as regex and memorize it in the {@link #rbelValidator} instance.
     *
     * @param path     path to match
     * @param rbelPath rbel path to node/attribute
     * @param value    value to match at given node/attribute
     */
    @Wenn("TGR finde die nächste Anfrage mit Pfad {string} und Knoten {string} der mit {string} übereinstimmt")
    @When("TGR find next request to path {string} with {string} matching {string}")
    public void findNextRequestToPathWithCommand(final String path, final String rbelPath, final String value) {
        final String parsedPath = TokenSubstituteHelper.substitute(path, "", new TestContext().getContext("tiger"));
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String parsedValue = TokenSubstituteHelper.substitute(value, "", new TestContext().getContext("tiger"));
        rbelValidator.filterRequestsAndStoreInContext(parsedPath, parsedRbelPath, parsedValue, true);
    }

    /**
     * assert that there is any message with given rbel path node/attribute matching given value. The result (request or response) will not
     * be stored in the {@link #rbelValidator} instance.
     *
     * @param rbelPath rbel path to node/attribute
     * @param value    value to match at given node/attribute
     * @deprecated
     */
    @Wenn("TGR finde eine Nachricht mit Knoten {string} der mit {string} übereinstimmt")
    @When("TGR any message with attribute {string} matches {string}")
    public void findAnyMessageAttributeMatches(final String rbelPath, final String value) {
        final String parsedValue = TokenSubstituteHelper.substitute(value, "", new TestContext().getContext("tiger"));
        rbelValidator.getRbelMessages().stream()
            .filter(msg -> new RbelPathExecutor(msg, rbelPath).execute()
                .get(0).getRawStringContent().equals(parsedValue))
            .findAny()
            .orElseThrow(() -> new AssertionError(
                "No message with matching value '" + value + "' at path '" + rbelPath + "'"));
    }

    // =================================================================================================================
    //
    //    S T O R E   R E S P O N S E   N O D E   I N   C O N T E X T
    //
    // =================================================================================================================

    /**
     * store given rbel path node/attribute text value of curren tresponse.
     *
     * @param rbelPath path to node/attribute
     * @param varName  name of variable to store the node text value in
     */
    @Dann("TGR speichere Wert des Knotens {string} der aktuellen Antwort in der Variable {string}")
    @Then("TGR store current response node text value at {string} in variable {string}")
    public void storeCurrentResponseNodeTextValueInVariable(final String rbelPath, final String varName) {
        final String text = rbelValidator.findElemInLastResponse(rbelPath).getRawStringContent();
        new TestContext("tiger").putString(varName, text);
    }

    // =================================================================================================================
    //
    //    R E S P O N S E   V A L I D A T I O N
    //
    // =================================================================================================================

    /**
     * assert that response body of filtered request matches.
     *
     * @param docString value / regex that should equal or match
     */
    @Dann("TGR prüfe aktuelle Antwort stimmt im Body überein mit:")
    @Then("TGR current response body matches")
    public void currentResponseBodyMatches(final String docString) {
        final String parsedDocString = TokenSubstituteHelper.substitute(docString, "", new TestContext().getContext("tiger"));

        currentResponseMessageAtMatches("$.body", parsedDocString);
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute.
     *
     * @param rbelPath path to node/attribute
     * @param value    value / regex that should equal or match as string content with MultiLine and DotAll regex option
     */
    @Dann("TGR prüfe aktuelle Antwort stimmt im Knoten {string} überein mit {string}")
    @Then("TGR current response with attribute {string} matches {string}")
    public void currentResponseMessageAttributeMatches(final String rbelPath, final String value) {
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String text = rbelValidator.findElemInLastResponse(parsedRbelPath).getRawStringContent();
        final String parsedValue = TokenSubstituteHelper.substitute(value, "", new TestContext().getContext("tiger"));
        if (!text.equals(parsedValue)) {
            assertThat(text).matches(Pattern.compile(parsedValue, Pattern.MULTILINE | Pattern.DOTALL));
        }
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute.
     *
     * @param rbelPath  path to node/attribute
     * @param docString value / regex that should equal or match as string content with MultiLine and DotAll regex option supplied as
     *                  DocString
     */
    @Dann("TGR prüfe aktuelle Antwort im Knoten {string} stimmt überein mit:")
    @Then("TGR current response at {string} matches")
    public void currentResponseMessageAtMatchesDocString(final String rbelPath, final String docString) {
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String parsedDocString = TokenSubstituteHelper.substitute(docString, "", new TestContext().getContext("tiger"));
        currentResponseMessageAtMatches(parsedRbelPath, parsedDocString);
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute.
     *
     * @param rbelPath path to node/attribute
     * @param value    value / regex that should equal or match as string content with MultiLine and DotAll regex option
     * @deprecated
     */
    @Then("TGR current response at {string} matches {string}")
    public void currentResponseMessageAtMatches(final String rbelPath, final String value) {
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String parsedValue = TokenSubstituteHelper.substitute(value, "", new TestContext().getContext("tiger"));
        currentResponseMessageAttributeMatches(parsedRbelPath, parsedValue);
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute assuming its JSON or XML
     *
     * @param rbelPath     path to node/attribute
     * @param mode         one of JSON|XML
     * @param oracleDocStr value / regex that should equal or match as JSON content
     * @see JsonChecker#assertJsonObjectShouldMatchOrContainInAnyOrder(String, String, boolean)
     */
    @Dann("TGR prüfe aktuelle Antwort im Knoten stimmt als {word} überein mit:")
    @Then("TGR current response at {string} matches as {word}")
    public void currentResponseAtMatchesAsJson(final String rbelPath, final String mode, final String oracleDocStr) {
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String parsedOracleDocStr = TokenSubstituteHelper.substitute(oracleDocStr, "", new TestContext().getContext("tiger"));
        switch (mode.toUpperCase()) {
            case "JSON":
                new JsonChecker().assertJsonObjectShouldMatchOrContainInAnyOrder(
                    rbelValidator.findElemInLastResponse(parsedRbelPath).getRawStringContent(),
                    parsedOracleDocStr,
                    false);
                break;
            case "XML":
                final RbelElement el = rbelValidator.findElemInLastResponse(parsedRbelPath);
                assertThat(el.hasFacet(RbelXmlFacet.class))
                    .withFailMessage("Node '" + rbelPath + "' is not XML")
                    .isTrue();
                rbelValidator.compareXMLStructure(
                    el.getFacetOrFail(RbelXmlFacet.class).getSourceElement().asXML(),
                    parsedOracleDocStr);

        }
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute assuming its XML with given list of diff options.
     *
     * @param rbelPath       path to node/attribute
     * @param diffOptionsCSV a csv separated list of diff option identifiers to be applied to comparison of the two XML sources
     *                       <ul>
     *                           <li>nocomment ... {@link DiffBuilder#ignoreComments()}</li>
     *                           <li>txtignoreempty ... {@link  DiffBuilder#ignoreElementContentWhitespace()}</li>
     *                           <li>txttrim ... {@link DiffBuilder#ignoreWhitespace()}</li>
     *                           <li>txtnormalize ... {@link DiffBuilder#normalizeWhitespace()}</li>
     *                       </ul>
     * @param xmlDocStr      value / regex that should equal or match as JSON content
     * @see <a href="https://github.com/xmlunit/user-guide/wiki/DifferenceEvaluator">More on DifferenceEvaluator</a>
     */
    @Dann("TGR prüfe aktuelle Antwort im Knoten {string} stimmt als XML mit folgenden diff Optionen {string} überein mit:")
    @Then("TGR current response at {string} matches as XML and diff options {string}")
    public void currentResponseAtMatchesAsXMLAndDiffOptions(final String rbelPath, String diffOptionsCSV,
        final String xmlDocStr) {
        final String parsedRbelPath = TokenSubstituteHelper.substitute(rbelPath, "", new TestContext().getContext("tiger"));
        final String parsedXmlDocStr = TokenSubstituteHelper.substitute(xmlDocStr, "", new TestContext().getContext("tiger"));
        final RbelElement el = rbelValidator.findElemInLastResponse(parsedRbelPath);
        assertThat(el.hasFacet(RbelXmlFacet.class)).withFailMessage("Node '" + rbelPath + "' is not XML").isTrue();
        rbelValidator.compareXMLStructure(el.getFacetOrFail(RbelXmlFacet.class).getSourceElement().asXML(), parsedXmlDocStr,
            diffOptionsCSV);
    }
}
