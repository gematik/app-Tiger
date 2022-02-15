/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.lib.parser.model.Result;
import de.gematik.test.tiger.lib.parser.model.TestResult;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TestJUnitTestResultParser {

    @Disabled //TODO TGR-373
    @Test
    void testJunitResultParseOK() {
        // avoid xerces bug not supporting accessEXternalDTD
        System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
            "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");

        final JUnitTestResultParser parser = new JUnitTestResultParser();

        final Map<String, TestResult> results = new HashMap<>();
        parser.parseDirectoryForResults(results,
            Paths.get("src", "test", "resources", "testdata", "parser", "junit").toFile());

        assertThat(results.keySet()).hasSize(357);
        assertThat(
            results.get("de.gematik.idp.tests.aforeport.TestAfoJUnitTestResultParser:testJunitResultParseOK")
                .getStatus())
            .isEqualTo(Result.FAILED);
    }

    @Test
    public void parseJUnitResultsInvalidRoot() {
        final JUnitTestResultParser parser = new JUnitTestResultParser();

        final Map<String, TestResult> results = new HashMap<>();
        parser.parseDirectoryForResults(results, Paths.get("src", "test", "resources", "bdd-NonExisting").toFile());

        assertThat(results.keySet()).hasSize(0);
    }
}
