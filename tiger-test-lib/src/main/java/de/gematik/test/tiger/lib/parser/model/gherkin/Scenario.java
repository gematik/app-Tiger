/*
 * Copyright (c) 2021 gematik GmbH
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.json.JSONArray;
import org.json.JSONObject;

@EqualsAndHashCode(callSuper = true)
@Data
public class Scenario extends GherkinStruct {

    private Feature feature;
    private List<Step> steps = new ArrayList<>();

    @Override
    public Tag getTag(final String tagName) {
        final Tag tag = super.getTag(tagName);
        if (tag == null) {
            return feature.getTag(tagName);
        } else {
            return tag;
        }
    }

    public boolean hasTag(final String tagName) {
        return getTag(tagName) != null;
    }

    @Override
    public String toString() {
        return super.toString() + " , Feature=" + feature.getName();
    }

    public JSONObject toPolarionJSON() {
        final JSONObject json = new JSONObject();

        if (hasTag("@TCID")) {
            json.put(JSON.INTERNE_ID, getTag("@TCID").getParameter());
        }
        if (hasTag("@Ready")) {
            json.put(JSON.STATUS, "Implementiert");
        } else {
            json.put(JSON.STATUS, "In Bearbeitung");
        }
        if (hasTag("@Negative")) {
            json.put(JSON.NEGATIVE_TF, true);
        } else {
            json.put(JSON.NEGATIVE_TF, false);
        }
        if (hasTag("@PRIO")) {
            json.put(JSON.PRIO, Integer.parseInt(getTag("@PRIO").getParameter()));
        }
        if (hasTag("@Product")) {
            json.put(JSON.PRODUKT_TYP, feature.getTag("@Product").getParameter());
        }
        if (hasTag("@manual")) {
            json.put(JSON.MODUS, "Manuell");
        } else {
            json.put(JSON.MODUS, "Automatisch");
        }
        json.put(JSON.TESTSTUFE, "Produkttest");
        json.put(JSON.TESTART, "Funktionstest");
        final JSONArray afos = new JSONArray(getTags().stream()
            .filter(tag -> tag.getName().equals("@Afo"))
            .map(Tag::getParameter)
            .collect(Collectors.toList()));
        json.put(JSON.AFOLINKS, afos);
        json.put(JSON.TITEL, getName());
        json.put(JSON.DESCRIPTION, getDescription().replace("\n", "</br>"));
        json.put(JSON.VORBEDINGUNG, "");
        final StringBuilder sb = new StringBuilder();
        int stepIdx = 0;
        for (final Step step : steps) {
            stepIdx++;
            switch (step.getKeyword()) {
                case "Given":
                    sb.append("<b>Setup</b><br/>");
                    break;
                case "When":
                    sb.append("<hr/><b>Aktion</b><br/>");
                    break;
                case "Then":
                    sb.append("<hr/><b>Prüfung</b><br/>");
                    break;
            }
            addStep(step, stepIdx + ": ", sb);
        }
        json.put(JSON.TESTABLAUF, sb.toString());
        return json;
    }

    protected void addStep(final Step step, final String header, final StringBuilder sb) {
        sb.append("<pre>" + header);
        step.getLines().stream().forEach(
            line -> sb.append("    ")
                .append(line.replace("<", "&lt;").replace(">", "&gt;"))
                .append("<br/>")
        );
        sb.append("</pre>");
    }
}
