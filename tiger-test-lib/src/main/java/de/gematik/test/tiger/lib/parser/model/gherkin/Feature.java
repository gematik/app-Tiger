/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.parser.model.gherkin;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class Feature extends GherkinStruct {

    private String fileName;
    private Background background;
    private List<GherkinStruct> scenarios = new ArrayList<>();

    public Scenario getScenario(String name, Integer lineNumber) {
        return scenarios.stream().filter(scenario -> scenario.getName().equals(name))
            .map(Scenario.class::cast)
            .filter(scenario -> scenario instanceof ScenarioOutline || scenario.getLineNumber().equals(lineNumber))
            .findFirst().orElseThrow();
    }
}
