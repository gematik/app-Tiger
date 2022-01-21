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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.json.JSONObject;

@EqualsAndHashCode(callSuper = true)
@Data
public class ScenarioOutline extends Scenario {

    private Step examples;

    @Override
    public JSONObject toPolarionJSON() {
        final JSONObject json = super.toPolarionJSON();
        final StringBuilder sb = new StringBuilder();
        addStep(examples, "", sb);
        json.put(JSON.DATAVARIANTS, sb.toString());
        return json;
    }

    public List<String> getExampleKeys(){
        List<String> keys = Arrays.stream(examples.getLines().stream()
                .filter(line -> line.trim().startsWith("|")).findFirst().orElseThrow().split("\\|"))
            .map(String::trim).collect(Collectors.toList());
        keys.remove(0);
        return keys;
    }

    public List<Map<String, String>> getExamplesAsList(){
        List<String> keys = getExampleKeys();
        return  examples.getLines().stream()
            .filter(line -> line.trim().startsWith("|"))
            .skip(1)
            .map(line -> {
                List<String> values = Arrays.stream(line.split("\\|")).map(String::trim).collect(Collectors.toList());
                values.remove(0);
                Map<String,String> entries = new HashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    entries.put(keys.get(i), values.get(i));
                }
                return entries;
            }).collect(Collectors.toList());
    }
}
