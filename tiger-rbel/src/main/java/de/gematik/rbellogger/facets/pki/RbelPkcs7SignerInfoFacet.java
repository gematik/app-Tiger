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
package de.gematik.rbellogger.facets.pki;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h3;
import static j2html.TagCreator.h4;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.EmailConversionUtils;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RbelPkcs7SignerInfoFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new AbstractX509FacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelPkcs7SignerInfoFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            var signerInfoFacet = element.getFacetOrFail(RbelPkcs7SignerInfoFacet.class);
            var renderedRecipients =
                EmailConversionUtils.renderRecipientEmails(
                    renderingToolkit, signerInfoFacet.getSignedAttributes());

            return div(h3().withClass("title").withText("Signer Info"))
                .with(renderValueChildren(signerInfoFacet.getChildElements()))
                .with(
                    vertParentTitle()
                        .with(
                            div(h4().withClass("title").withText("Signer Identifier"))
                                .with(
                                    renderValueChildren(
                                        signerInfoFacet
                                            .getSignerId()
                                            .getFacetOrFail(CmsEntityIdentifierFacet.class)
                                            .getChildElements()))))
                .with(
                    div(h4().withClass("title").withText("Recipient Email Addresses"))
                        .with(renderedRecipients));
          }
        });
  }

  final RbelElement contentType;
  final RbelElement encryptionAlgorithm;
  final RbelElement digestAlgorithm;
  final RbelElement signature;
  final RbelElement signerId;
  final RbelElement counterSignatures;
  final RbelElement signedAttributes;
  final RbelElement unsignedAttributes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("contentType", contentType)
        .with("digestAlgorithm", digestAlgorithm)
        .with("encryptionAlgorithm", encryptionAlgorithm)
        .with("signature", signature)
        .with("counterSignatures", counterSignatures)
        .with("signerId", signerId)
        .withSkipIfNull("signedAttributes", signedAttributes)
        .withSkipIfNull("unsignedAttributes", unsignedAttributes);
  }
}
