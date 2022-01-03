/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestSerenityTestParser {

    @Test
    public void parseSerenityResultsOK() {
        final SerenityTestResultParser parser = new SerenityTestResultParser();

        final Map<String, TestResult> results = new HashMap<>();
        parser.parseDirectoryForResults(results,
            Paths.get("src", "test", "resources", "testdata", "parser", "bdd").toFile());

        assertThat(results).containsOnlyKeys(
            "fordere-access-token-mittels-sso-token-an:gettoken-mit-sso-token---veralteter-sso-token-wird-abgelehnt",
            "fordere-access-token-mit-einer-signierten-challenge-an:gettoken-signierte-challenge---veralteter-token-code-wird-abgelehnt");
    }

    @Test
    public void parseSerenityResultsInvalidRoot() {
        final SerenityTestResultParser parser = new SerenityTestResultParser();

        final Map<String, TestResult> results = new HashMap<>();
        parser.parseDirectoryForResults(results, Paths.get("src", "test", "resources", "bdd-NonExisting").toFile());

        assertThat(results.keySet()).hasSize(0);
    }
}
