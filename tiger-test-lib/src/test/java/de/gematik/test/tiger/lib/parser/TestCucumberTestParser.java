/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.lib.parser.model.Testcase;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestCucumberTestParser {

    @Test
    public void parseCucumberFeaturesOK() {
        final CucumberTestParser parser = new CucumberTestParser();

        parser.parseDirectory(Paths.get("src", "test", "resources", "testdata", "parser", "bdd").toFile());
        final Map<String, List<Testcase>> tcs = parser.getParsedTestcasesPerAfo();

        assertThat(tcs).containsOnlyKeys("A_19874", "A_20688", "A_20457", "A_20623", "A_20668", "A_20614", "A_20591",
            "A_20313", "A_20731", "A_20314", "A_20952", "A_20315", "A_20327", "A_20625", "A_21321",
            "A_21320", "A_20463", "A_20464", "A_20310", "A_20321", "A_20521", "A_20523", "A_20604",
            "A_20440", "A_20698", "A_20376", "A_20377", "A_21317", "A_21472", "A_20465", "A_20697",
            "A_20951", "A_20699", "A_20318", "A_20319", "A_20693", "A_20695");
        assertThat(tcs.get("A_20623")).hasSize(1);
        assertThat(tcs.get("A_20623").get(0).getMethod())
            .isEqualTo("disc---discovery-dokument-muss-signiert-sein");
    }

    @Test
    public void parseCucumberFeaturesInvalidRoot() {
        final CucumberTestParser parser = new CucumberTestParser();

        parser.parseDirectory(Paths.get("src", "test", "resources", "bdd-NonExisting").toFile());
        final Map<String, List<Testcase>> tcs = parser.getParsedTestcasesPerAfo();

        assertThat(tcs.keySet()).hasSize(0);
    }
}
