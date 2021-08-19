package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelXmlFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.lib.rbel.RbelMessageValidator;
import io.cucumber.java.en.And;
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
     * find the first request where the path equals or matches as regex and memorize it in the {@link #rbelValidator}
     * instance
     *
     * @param path path to match
     */
    @And("TGR find request to path {string}")
    public void findRequestToPath(final String path) {
        rbelValidator.filterRequestsAndStoreInContext(path, null, null);
    }

    /**
     * find the first request where path and node value equal or match as regex and memorize it in the {@link
     * #rbelValidator} instance.
     *
     * @param path     path to match
     * @param rbelPath rbel path to node/attribute
     * @param value    value to match at given node/attribute
     */
    @And("TGR find request to path {string} with {string} matching {string}")
    public void findRequestToPathWithCommand(
        final String path, final String rbelPath, final String value) {
        rbelValidator.filterRequestsAndStoreInContext(path, rbelPath, value);
    }

    /**
     * find the NEXT request where the path equals or matches as regex and memorize it in the {@link #rbelValidator}
     * instance.
     *
     * @param path path to match
     */
    @And("TGR find next request to path {string}")
    public void findNextRequestToPath(final String path) {
        rbelValidator.filterNextRequestAndStoreInContext(path, null, null);
    }

    /**
     * find the NEXT request where path and node value equal or match as regex and memorize it in the {@link
     * #rbelValidator} instance.
     *
     * @param path     path to match
     * @param rbelPath rbel path to node/attribute
     * @param value    value to match at given node/attribute
     */
    @And("TGR find next request to path {string} with {string} matching {string}")
    public void findNextRequestToPathWithCommand(
        final String path, final String rbelPath, final String value) {
        rbelValidator.filterNextRequestAndStoreInContext(path, rbelPath, value);
    }

    /**
     * assert that there is any message with given rbel path node/attribute matching given value. The result (request or
     * response) will not be stored in the {@link #rbelValidator} instance.
     *
     * @param rbelPath rbel path to node/attribute
     * @param value    value to match at given node/attribute
     * @deprecated
     */
    @And("TGR any message with attribute {string} matches {string}")
    public void findAnyMessageAttributeMatches(final String rbelPath, final String value) {
        rbelValidator.getRbelMessages().stream()
            .filter(msg -> new RbelPathExecutor(msg, rbelPath).execute()
                .get(0).getRawStringContent().equals(value))
            .findAny()
            .orElseThrow(() -> new AssertionError(
                "No message with matching value '" + value + "' at path '" + rbelPath + "'"));
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
    @And("TGR current response body matches")
    public void currentResponseAtMatches(final String docString) {
        String bodyStr = rbelValidator.findElemInLastResponse("$.body").getRawStringContent();
        if (!bodyStr.equals(docString)) {
            assertThat(bodyStr).matches(docString);
        }
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute.
     *
     * @param rbelPath path to node/attribute
     * @param value    value / regex that should equal or match as string content with MultiLine and DotAll regex
     *                 option
     */
    @And("TGR current response with attribute {string} matches {string}")
    public void currentResponseMessageAttributeMatches(final String rbelPath, final String value) {
        final String text = rbelValidator.findElemInLastResponse(rbelPath).getRawStringContent();
        if (!text.equals(value)) {
            assertThat(text).matches(Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL));
        }
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute.
     *
     * @param rbelPath path to node/attribute
     * @param value    value / regex that should equal or match as string content with MultiLine and DotAll regex
     *                 option
     */
    @And("TGR current response at {string} matches {string}")
    public void currentResponseMessageAtMatches(final String rbelPath, final String value) {
        final String text = rbelValidator.findElemInLastResponse(rbelPath).getRawStringContent();
        if (!text.equals(value)) {
            assertThat(text).matches(Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL));
        }
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute.
     *
     * @param rbelPath  path to node/attribute
     * @param docString value / regex that should equal or match
     */
    @And("TGR current response at {string} matches")
    public void currentResponseAtMatches(final String rbelPath, final String docString) {
        String bodyStr = rbelValidator.findElemInLastResponse(rbelPath).getRawStringContent();
        if (!bodyStr.equals(docString)) {
            assertThat(bodyStr).matches(docString);
        }
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute assuming its JSON
     *
     * @param rbelPath  path to node/attribute
     * @param oracleStr value / regex that should equal or match as JSON content
     * @see JsonChecker#assertJsonObjectShouldMatchOrContainInAnyOrder(String, String, boolean)
     */
    @And("TGR current response at {string} matches as JSON")
    public void currentResponseAtMatchesAsJson(final String rbelPath, final String oracleStr) {
        String jsonStr = rbelValidator.findElemInLastResponse(rbelPath).getRawStringContent();
        new JsonChecker().assertJsonObjectShouldMatchOrContainInAnyOrder(jsonStr, oracleStr, false);
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute assuming its XML.
     *
     * @param rbelPath path to node/attribute
     * @param XMLstr   value / regex that should equal or match as JSON content
     * @see <a href="https://github.com/xmlunit/user-guide/wiki/DifferenceEvaluator">More on DifferenceEvaluator</a>
     */
    @And("TGR current response at {string} matches as XML")
    public void currentResponseAtMatchesAsXML(final String rbelPath, final String XMLstr) {
        final RbelElement el = rbelValidator.findElemInLastResponse(rbelPath);
        assertThat(el.hasFacet(RbelXmlFacet.class)).withFailMessage("Node '" + rbelPath + "' is not XML").isTrue();
        rbelValidator.compareXMLStructure(el.getFacetOrFail(RbelXmlFacet.class).toString(), XMLstr);
    }

    /**
     * assert that response of filtered request matches at given rbel path node/attribute assuming its XML with given
     * list of diff options.
     *
     * @param rbelPath       path to node/attribute
     * @param XMLstr         value / regex that should equal or match as JSON content
     * @param diffOptionsCSV a csv separated list of diff option identifiers to be applied to comparison of the two XML
     *                       sources
     *                       <ul>
     *                           <li>nocomment ... {@link DiffBuilder#ignoreComments()}</li>
     *                           <li>txtignoreempty ... {@link  DiffBuilder#ignoreElementContentWhitespace()}</li>
     *                           <li>txttrim ... {@link DiffBuilder#ignoreWhitespace()}</li>
     *                           <li>txtnormalize ... {@link DiffBuilder#normalizeWhitespace()}</li>
     *                       </ul>
     * @see <a href="https://github.com/xmlunit/user-guide/wiki/DifferenceEvaluator">More on DifferenceEvaluator</a>
     */
    @And("TGR current response at {string} matches as XML and diff options {string}")
    public void currentResponseAtMatchesAsXMLAndDiffOptions(final String rbelPath, final String XMLstr,
        String diffOptionsCSV) {
        final RbelElement el = rbelValidator.findElemInLastResponse(rbelPath);
        assertThat(el.hasFacet(RbelXmlFacet.class)).withFailMessage("Node '" + rbelPath + "' is not XML").isTrue();
        rbelValidator.compareXMLStructure(el.getFacetOrFail(RbelXmlFacet.class).toString(), XMLstr, diffOptionsCSV);
    }
}
