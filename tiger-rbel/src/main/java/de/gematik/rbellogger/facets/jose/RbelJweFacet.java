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
package de.gematik.rbellogger.facets.jose;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

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
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class RbelJweFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelJweFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            RbelElement jweHeader = element.getFacetOrFail(RbelJweFacet.class).getHeader();
            RbelElement jweBody = element.getFacetOrFail(RbelJweFacet.class).getBody();
            return div(t1ms("JWE").with(showContentButtonAndDialog(element, renderingToolkit)))
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(
                                            t2("Headers")
                                                .with(
                                                    showContentButtonAndDialog(
                                                        jweHeader, renderingToolkit))
                                                .with(addNotes(jweHeader))
                                                .with(renderingToolkit.convert(jweHeader))),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(
                                            t2("Body")
                                                .with(
                                                    showContentButtonAndDialog(
                                                        jweBody, renderingToolkit))
                                                .with(addNotes(jweBody)))
                                        .with(renderingToolkit.convert(jweBody)),
                                    renderingToolkit.convert(
                                        element
                                            .getFacetOrFail(RbelJweFacet.class)
                                            .getEncryptionInfo()))));
          }
        });
  }

  private final RbelElement header;
  private final RbelElement body;
  private final RbelElement encryptionInfo;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("header", header)
        .with("body", body)
        .with("encryptionInfo", encryptionInfo);
  }
}
