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
package de.gematik.rbellogger.facets.sicct;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RbelSicctCommandFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelSicctCommandFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final var sicctCommand = element.getFacetOrFail(RbelSicctCommandFacet.class);
            return div(t1ms("SICCT Command")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("Header"))
                                        .with(renderingToolkit.convert(sicctCommand.getHeader())),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(t2("Body"))
                                        .with(
                                            renderingToolkit.printAsBinary(
                                                sicctCommand.getBody())))));
          }
        });
  }

  private RbelElement header;
  private RbelElement body;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("header", header).with("body", body);
  }
}
