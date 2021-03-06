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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class GherkinStruct {

    public static final List<String> STRUCT_NAMES = List.of(
        "Feature", "Background", "Scenario", "ScenarioOutline",
        "Funktionalität", "Funktion", "Grundlage", "Hintergrund",
        "Voraussetzungen", "Vorbedingungen", "Szenario",
        "Szenarien", "Szenariogrundriss");

    public static final Map<String, String> STRUCT_I18N_MAP = Map.of(
        "Funktionalität", "Feature",
        "Funktion", "Feature",
        "Grundlage", "Background",
        "Hintergrund", "Background",
        "Voraussetzungen", "Background",
        "Vorbedingungen", "Background",
        "Szenario", "Scenario",
        "Szenarien", "ScenarioOutline",
        "Szenariogrundriss", "ScenarioOutline"
    );

    private String name;
    private String description = "";
    private List<Tag> tags = new ArrayList<>();

    private String id;


    protected Tag getTag(final String tagName) {
        return tags.stream()
            .filter(tag -> tag.getName().equals(tagName))
            .findFirst()
            .orElse(null);
    }
}
