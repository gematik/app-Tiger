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

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.JsonNoteEntry;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RbelJsonFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelJsonFacet.class)
                    && element.getFacet(RbelRootFacet.class)
                    .filter(root -> root.getRootFacet() instanceof RbelJsonFacet)
                    .isPresent();
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                RbelHtmlRenderingToolkit renderingToolkit) {
                String formatedJson = renderingToolkit.GSON.toJson(
                    renderingToolkit.shadeJson(
                        JsonParser.parseString(element.getRawStringContent()),
                        Optional.empty(),
                        element
                    ));
                for (final Entry<UUID, JsonNoteEntry> entry : renderingToolkit.getNoteTags().entrySet()) {
                    if (formatedJson.contains(entry.getValue().getStringToMatch() + ",")) {
                        formatedJson = formatedJson.replace(
                            entry.getValue().getStringToMatch() + ",",
                            entry.getValue().getTagForKeyReplacement().render() + "," + entry.getValue().getTagForValueReplacement()
                                .render());
                    } else if (formatedJson.contains(entry.getValue().getStringToMatch())) {
                        formatedJson = formatedJson.replace(
                            entry.getValue().getStringToMatch(),
                            entry.getValue().getTagForKeyReplacement().render()
                                + entry.getValue().getTagForValueReplacement().render());
                    }
                }
                return ancestorTitle()
                    .with(
                        vertParentTitle().with(
                            div().withClass("tile is-child pr-3").with(
                                pre(new UnescapedText(formatedJson))
                                    .withClass("json")
                            ).with(renderingToolkit.convertNested(element))));
            }
        });
    }

    private final JsonElement jsonElement;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap();
    }
}
