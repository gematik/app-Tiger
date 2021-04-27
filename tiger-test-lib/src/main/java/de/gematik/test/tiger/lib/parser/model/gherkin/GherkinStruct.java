package de.gematik.test.tiger.lib.parser.model.gherkin;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GherkinStruct {

    public static final List<String> STRUCT_NAMES = List.of("Feature", "Background", "Scenario", "ScenarioOutline");

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
