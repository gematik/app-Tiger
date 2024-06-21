/*
 * Copyright (c) 2024 gematik GmbH
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
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RbelPop3ResponseFacet implements RbelFacet {

  private RbelElement status;
  @Nullable private RbelElement header;
  @Nullable private RbelElement body;

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelPop3ResponseFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelPop3ResponseFacet facet = element.getFacetOrFail(RbelPop3ResponseFacet.class);
            return div(
                h2().withClass("title").withText("POP3 Response"),
                p().with(b().withText("Status: "))
                    .withText(
                        Optional.ofNullable(facet.getStatus().getRawStringContent()).orElse("")),
                p().with(b().withText("Header: "))
                    .withText(
                        Optional.ofNullable(facet.getHeader())
                            .map(RbelElement::getRawStringContent)
                            .orElse("")),
                br(),
                ancestorTitle()
                    .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }
        });
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("status", status)
        .withSkipIfNull("header", header)
        .withSkipIfNull("body", body);
  }
}
