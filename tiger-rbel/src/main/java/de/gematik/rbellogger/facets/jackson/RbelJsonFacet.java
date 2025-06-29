/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.jackson;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.JsonNoteEntry;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;

@Data
@Builder(toBuilder = true)
public class RbelJsonFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element
                .getFacet(RbelJsonFacet.class)
                .filter(
                    jsonFacet ->
                        element.getFacets().stream()
                            .filter(RbelRootFacet.class::isInstance)
                            .map(RbelRootFacet.class::cast)
                            .map(RbelRootFacet::getRootFacet)
                            .anyMatch(jsonFacet::equals))
                .isPresent();
          }

          @SneakyThrows
          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            String formatedJson =
                renderingToolkit
                    .getObjectMapper()
                    .writeValueAsString(
                        renderingToolkit.shadeJson(
                            renderingToolkit
                                .getObjectMapper()
                                .readTree(element.getRawStringContent()),
                            Optional.empty(),
                            element));
            for (final Entry<UUID, JsonNoteEntry> entry :
                renderingToolkit.getNoteTags().entrySet()) {
              if (formatedJson.contains(entry.getValue().getStringToMatch() + ",")) {
                formatedJson =
                    formatedJson.replace(
                        entry.getValue().getStringToMatch() + ",",
                        entry.getValue().getTagForKeyReplacement().render()
                            + ","
                            + entry.getValue().getTagForValueReplacement().render());
              } else if (formatedJson.contains(entry.getValue().getStringToMatch())) {
                formatedJson =
                    formatedJson.replace(
                        entry.getValue().getStringToMatch(),
                        entry.getValue().getTagForKeyReplacement().render()
                            + entry.getValue().getTagForValueReplacement().render());
              }
            }
            return ancestorTitle()
                .with(
                    vertParentTitle()
                        .with(
                            div()
                                .withClass("tile is-child pe-3")
                                .with(
                                    pre(new UnescapedText(formatedJson))
                                        .withClass("json language-json"))
                                .with(renderingToolkit.convertNested(element))));
          }
        });
  }

  private final JsonNode jsonElement;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
