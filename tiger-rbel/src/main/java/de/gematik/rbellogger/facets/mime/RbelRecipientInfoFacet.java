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
package de.gematik.rbellogger.facets.mime;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h3;
import static j2html.TagCreator.h4;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.facets.pki.AbstractX509FacetRenderer;
import de.gematik.rbellogger.facets.pki.CmsEntityIdentifierFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
class RbelRecipientInfoFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new AbstractX509FacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelRecipientInfoFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            var recipientInfoFacet = element.getFacetOrFail(RbelRecipientInfoFacet.class);
            return div(h3().withClass("title").withText("Recipient Info"))
                .with(renderValueChildren(recipientInfoFacet.getChildElements()))
                .with(
                    vertParentTitle()
                        .with(
                            div(h4().withClass("title").withText("Recipient Identifier"))
                                .with(
                                    renderValueChildren(
                                        recipientInfoFacet
                                            .getRecipientId()
                                            .getFacetOrFail(CmsEntityIdentifierFacet.class)
                                            .getChildElements()))));
          }
        });
  }

  final RbelElement contentType;
  final RbelElement keyEncryptionAlgorithm;
  final RbelElement recipientId;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("contentType", contentType)
        .with("keyEncryptionAlgorithm", keyEncryptionAlgorithm)
        .withSkipIfNull("recipientId", recipientId);
  }
}
