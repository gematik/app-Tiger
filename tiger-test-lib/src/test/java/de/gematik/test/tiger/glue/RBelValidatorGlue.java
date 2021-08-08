package de.gematik.test.tiger.glue;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.data.facet.RbelHttpResponseFacet;
import de.gematik.rbellogger.data.facet.RbelJsonFacet;
import de.gematik.rbellogger.util.RbelPathExecutor;
import de.gematik.test.tiger.lib.json.JsonChecker;
import de.gematik.test.tiger.lib.rbel.RbelMessageValidator;
import io.cucumber.java.en.And;
import java.util.List;
import java.util.regex.Pattern;

public class RBelValidatorGlue {

    static RbelMessageValidator rbelValidator = new RbelMessageValidator();

    public static RbelMessageValidator getRbelValidator() {
        return rbelValidator;
    }

    @And("TGR current response with attribute {string} matches {string}")
    public void epaMessageAttributeMatches(final String rbelPath, final String value) {
        try {
            final List<RbelElement> elementResultList =
                new RbelPathExecutor(rbelValidator.getLastResponse(), rbelPath).execute();
            assertThat(elementResultList).hasSize(1);
            final String text = elementResultList.get(0).getRawStringContent();
            if (!text.equals(value)) {
                assertThat(text).matches(Pattern.compile(value, Pattern.MULTILINE | Pattern.DOTALL));
            }
        } catch (final Exception e) {
            throw new AssertionError("Path to attribute '" + rbelPath + "' not found!");
        }
    }

    @And("TGR current JSON response matches")
    public void currentJsonResponseMatches(final String docString) {
        String body = rbelValidator.getLastResponse().getFacetOrFail(RbelHttpMessageFacet.class)
            .getBody().getRawStringContent();
        new JsonChecker().assertJsonObjectShouldMatchOrContainInAnyOrder(body, docString, false);
    }

    @And("TGR current response matches")
    public void currentResponseMatches(final String docString) {
        String body = rbelValidator.getLastResponse().getFacetOrFail(RbelHttpMessageFacet.class)
            .getBody().getRawStringContent();
        if (!body.equals(docString)) {
            assertThat(body).matches(docString);
        }
    }

    @And("TGR any message with attribute {string} matches {string}")
    public void epaAnyMessageAttributeMatches(final String rbelPath, final String value) {
        rbelValidator.getRbelMessages().stream()
            .filter(msg -> new RbelPathExecutor(msg, rbelPath).execute()
                .get(0).getRawStringContent().equals(value))
            .findAny()
            .orElseThrow(() -> new AssertionError(
                "No message with matching value '" + value + "' at path '" + rbelPath + "'"));
    }

    @And("TGR message {int} attribute {string} matches as JSON")
    public void epaMessageAttributeMatchesAsJSON(
        final int msgIdx, final String rbelPath, final String jsonStr) {
        final RbelElement el =
            new RbelPathExecutor(rbelValidator.getRbelMessages().get(msgIdx - 1), rbelPath).execute().get(0);
        assertThat(el.hasFacet(RbelJsonFacet.class)).isTrue();
        new JsonChecker()
            .assertJsonObjectShouldMatchOrContainInAnyOrder(el.getRawStringContent(), jsonStr, false);
    }

    @And("TGR find request to path {string}")
    public void findRequestToPath(final String path) {
        rbelValidator.filterRequestsAndStoreInContext(path, null, null);
    }

    @And("TGR find next request to path {string}")
    public void findNextRequestToPath(final String path) {
        rbelValidator.filterNextRequestAndStoreInContext(path, null, null);
    }

    @And("TGR find request to path {string} with {string} matching {string}")
    public void findRequestToPathWithCommand(
        final String path, final String rbelPath, final String value) {
        rbelValidator.filterRequestsAndStoreInContext(path, rbelPath, value);
    }

    @And("TGR find next request to path {string} with {string} matching {string}")
    public void findNextRequestToPathWithCommand(
        final String path, final String rbelPath, final String value) {
        rbelValidator.filterNextRequestAndStoreInContext(path, rbelPath, value);
    }
}
