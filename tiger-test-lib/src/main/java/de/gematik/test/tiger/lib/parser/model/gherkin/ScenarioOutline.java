/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
