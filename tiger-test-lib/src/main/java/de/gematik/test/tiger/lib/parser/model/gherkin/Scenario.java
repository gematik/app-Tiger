/*
 * Copyright (c) 2023 gematik GmbH
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
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@EqualsAndHashCode(callSuper = true)
@Data
public class Scenario extends GherkinStruct {

    private Feature feature;
    private Integer lineNumber;
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

        if (hasTag("@PRIO")) {
            json.put(JSON.PRIO, Integer.parseInt(getTag("@PRIO").getParameter()));
        } else {
            json.put(JSON.PRIO, Integer.parseInt("1"));
        }

        if (hasTag("@MODUS")) {
            json.put(JSON.MODUS, getTag("@MODUS").getParameter());
        } else {
            json.put(JSON.MODUS, "Automatisch");
        }

        if (hasTag("@STATUS")) {
            json.put(JSON.STATUS, getTag("@STATUS").getParameter());
        } else {
            json.put(JSON.STATUS, "Implementiert");
        }

        json.put(JSON.NEGATIVE_TF,
            hasTag("@TESTFALL") && StringUtils.equals(getTag("@TESTFALL").getParameter(), "Negativ"));

        if (hasTag("@TESTSTUFE")) {
            json.put(JSON.TESTSTUFE, getTag("@TESTSTUFE").getParameter());
        } else {
            json.put(JSON.TESTSTUFE, "3");
        }

        final JSONArray anforderungen = new JSONArray(getTags().stream()
            .filter(tag -> tag.getName().equals("@AFO-ID"))
            .map(Tag::getParameter)
            .collect(Collectors.toList()));
        json.put(JSON.AFOLINKS, anforderungen);

        final JSONArray anwendungsfaelle = new JSONArray(getTags().stream()
            .filter(tag -> tag.getName().equals("@AF-ID"))
            .map(Tag::getParameter)
            .collect(Collectors.toList()));
        json.put(JSON.AF_ID, anwendungsfaelle);

        final JSONArray produkte = new JSONArray(feature.getTags().stream()
            .filter(tag -> tag.getName().equals("@PRODUKT"))
            .map(Tag::getParameter)
            .collect(Collectors.toList()));
        json.put(JSON.PRODUKT_TYP, produkte);

        if (hasTag("@DESCRIPTION")) {
            json.put(JSON.DESCRIPTION, getDescription().replace("\n", "</br>"));
        } else {
            json.put(JSON.DESCRIPTION, "");
        }

        json.put(JSON.TESTART, "Funktionstest");
        json.put(JSON.TITEL, getName());
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
        sb.append("<pre>").append(header);
        step.getLines().forEach(
            line -> sb.append("    ")
                .append(line.replace("<", "&lt;").replace(">", "&gt;"))
                .append("<br/>")
        );
        sb.append("</pre>");
    }
}
