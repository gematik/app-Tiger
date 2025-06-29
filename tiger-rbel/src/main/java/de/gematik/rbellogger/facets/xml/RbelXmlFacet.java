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
package de.gematik.rbellogger.facets.xml;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.JsonNoteEntry;
import j2html.tags.ContainerTag;
import j2html.tags.Text;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RbelXmlFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelXmlFacet.class)
                && element
                    .getFacet(RbelRootFacet.class)
                    .filter(root -> root.getRootFacet() instanceof RbelXmlFacet)
                    .isPresent();
          }

          @SuppressWarnings({"rawtypes", "java:S3740"})
          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            String formattedXml =
                RbelHtmlRenderingToolkit.prettyPrintXml(element.getRawStringContent());
            for (final Entry<UUID, JsonNoteEntry> entry :
                renderingToolkit.getNoteTags().entrySet()) {
              if (formattedXml.contains(entry.getValue().getStringToMatch() + ",")) {
                formattedXml =
                    formattedXml.replace(
                        entry.getValue().getStringToMatch() + ",",
                        entry.getValue().getTagForKeyReplacement().render()
                            + ","
                            + entry.getValue().getTagForValueReplacement().render());
              } else if (formattedXml.contains(entry.getValue().getStringToMatch())) {
                formattedXml =
                    formattedXml.replace(
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
                                .withClass("tile is-child pe-2")
                                .with(pre(new Text(formattedXml)).withClass("json language-xml"))
                                .with(renderingToolkit.convertNested(element))));
          }
        });
  }

  @Builder.Default private final RbelMultiMap<RbelElement> childElements = new RbelMultiMap<>();
  @Builder.Default private final String namespaceUri = null;
  @Builder.Default private final String namespacePrefix = null;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return childElements;
  }
}
