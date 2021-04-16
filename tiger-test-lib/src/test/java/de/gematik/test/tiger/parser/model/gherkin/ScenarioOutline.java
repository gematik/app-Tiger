package de.gematik.test.tiger.parser.model.gherkin;

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
}
