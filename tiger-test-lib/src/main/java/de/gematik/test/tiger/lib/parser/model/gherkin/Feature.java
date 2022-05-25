/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.lib.parser.model.gherkin;

import de.gematik.test.tiger.lib.parser.TestParserException;
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
            .findFirst().orElseThrow(() -> new TestParserException("Could not find scenario with name '"+name+"' at line " + lineNumber));
    }

    public Scenario getScenarioById(String id) {
        return scenarios.stream()
            .filter(scenario -> scenario.getId() != null)
            .filter(scenario -> scenario.getId().equals(id))
            .map(Scenario.class::cast)
            .findFirst().orElseThrow(() -> new TestParserException("Could not find scenario with id '"+id+"'"));
    }
}
