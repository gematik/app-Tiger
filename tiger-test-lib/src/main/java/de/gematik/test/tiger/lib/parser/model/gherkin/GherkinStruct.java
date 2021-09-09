/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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


    protected Tag getTag(final String tagName) {
        return tags.stream()
            .filter(tag -> tag.getName().equals(tagName))
            .findFirst()
            .orElse(null);
    }
}
