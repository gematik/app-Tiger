package de.gematik.test.tiger.lib.parser;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.test.tiger.lib.parser.model.gherkin.Feature;
import de.gematik.test.tiger.lib.parser.model.gherkin.Scenario;
import de.gematik.test.tiger.lib.parser.model.gherkin.ScenarioOutline;
import java.nio.file.Paths;
import org.junit.Test;

public class TestFeatureParser {

    @Test
    public void testGermanKeywords() {
        FeatureParser p = new FeatureParser();
        Feature f = p.parseFeatureFile(Paths.get("src", "test", "resources", "testdata", "parser", "bdd", "german.feature").toFile());

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

}
