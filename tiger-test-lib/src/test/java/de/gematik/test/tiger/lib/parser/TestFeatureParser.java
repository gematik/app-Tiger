/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.Scenario;
import de.gematik.test.tiger.lib.parser.model.gherkin.ScenarioOutline;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TestFeatureParser {

    @Test
    void testGermanKeywords() {
        FeatureParser p = new FeatureParser();
        Feature f = p.parseFeatureFile(
            Paths.get("src", "test", "resources", "testdata", "parser", "bdd", "german.feature")
                .toFile());

        assertThat(f.getBackground()).isNotNull();
        assertThat(f.getBackground().getSteps()).hasSize(10);
        assertThat(f.getScenarios()).hasSize(4);

        Scenario s0 = (Scenario) f.getScenarios().get(0);
        assertThat(s0.getDescription()).isEqualTo("Beschreibung1");
        assertThat(s0.getName()).isEqualTo("B");
        assertThat(s0.getSteps()).hasSize(10);
        assertThat(s0.getSteps().get(2).getLines().get(0)).isEqualTo("Gegeben seien 2");
        ScenarioOutline s1 = (ScenarioOutline) f.getScenarios().get(1);
        assertThat(s1.getDescription()).isEqualTo("Beschreibung2");
        assertThat(s1.getSteps()).hasSize(10);
        assertThat(s1.getExamples().getLines()).hasSize(3);
        Scenario s2 = (Scenario) f.getScenarios().get(2);
        assertThat(s2.getDescription()).isEmpty();
        assertThat(s2.getName()).isEqualTo("D");
        assertThat(s2.getSteps()).hasSize(10);
    }

    @Test
    void testSameNamesForScenario() {
        FeatureParser featureParser = new FeatureParser();
        Feature f = featureParser.parseFeatureFile(
            Paths.get("src", "test", "resources", "testdata", "parser", "bdd",
                "ScenarioWithSameName.feature").toFile());

        assertThat(f.getBackground()).isNull();
        assertThat(f.getScenarios()).hasSize(2);

        f.getScenarios().forEach(
            gherkinStruct -> assertThat(gherkinStruct.getName()).isEqualTo("Simple first test"));
        Scenario s0 = (Scenario) f.getScenarios().get(0);
        assertThat(s0.getSteps()).hasSize(4);
        assertThat(s0.getLineNumber()).isEqualTo(3);

        Scenario s1 = (Scenario) f.getScenarios().get(1);
        assertThat(s1.getSteps()).hasSize(5);
        assertThat(s1.getLineNumber()).isEqualTo(10);
    }

    @Test
    void testScenarioWithMultipleAfIdForFileAndScenarioCorrectlyAddedToPolarionJson() {
        FeatureParser featureParser = new FeatureParser();
        Feature f = featureParser.parseFeatureFile(
            Paths.get("src", "test", "resources", "testdata", "parser", "bdd",
                "ScenarioWithAfIdForFileAndScenario.feature").toFile());

        assertThat(f.getBackground()).isNull();
        assertThat(f.getScenarios()).hasSize(3);

        List<String> afIds;

        Scenario s0 = (Scenario) f.getScenarios().get(0);
        afIds = JsonPath.parse(s0.toPolarionJSON().toString()).read("$.Anwendungsfälle");
        assertThat(afIds).as(s0.getName() + " should have 4 AF-IDs").hasSize(4);

        Scenario s1 = (Scenario) f.getScenarios().get(1);
        afIds = JsonPath.parse(s1.toPolarionJSON().toString()).read("$.Anwendungsfälle");
        assertThat(afIds).as(s1.getName() + " should have 3 AF-IDs").hasSize(3);

        Scenario s2 = (Scenario) f.getScenarios().get(2);
        afIds = JsonPath.parse(s2.toPolarionJSON().toString()).read("$.Anwendungsfälle");
        assertThat(afIds).as(s2.getName() + " should have 2 AF-IDs").hasSize(2);
    }
}
