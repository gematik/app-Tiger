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
package de.gematik.rbellogger.facets.vau.vau_erp;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public class RbelVauErpFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelVauErpFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              final RbelElement element,
              final Optional<String> key,
              final RbelHtmlRenderingToolkit renderingToolkit) {
            RbelElement vauMessage = element.getFacetOrFail(RbelVauErpFacet.class).getMessage();
            return div(t1ms("VAU Encrypted Message (E-Rezept)")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(
                                            t2("Header"),
                                            Optional.ofNullable(
                                                    element
                                                        .getFacetOrFail(RbelVauErpFacet.class)
                                                        .getPVersionNumber())
                                                .map(
                                                    v ->
                                                        p(b("Version Number: "))
                                                            .withText(v.getRawStringContent()))
                                                .map(DomContent.class::cast)
                                                .orElse(span()),
                                            Optional.ofNullable(
                                                    element
                                                        .getFacetOrFail(RbelVauErpFacet.class)
                                                        .getRequestId())
                                                .map(
                                                    v ->
                                                        p(b("Request ID: "))
                                                            .withText(v.getRawStringContent()))
                                                .map(DomContent.class::cast)
                                                .orElse(span())),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(
                                            t2("Body")
                                                .with(
                                                    showContentButtonAndDialog(
                                                        vauMessage, renderingToolkit))
                                                .with(addNotes(vauMessage)))
                                        .with(
                                            renderingToolkit.convert(vauMessage, Optional.empty())),
                                    childBoxNotifTitle(CLS_PKIOK)
                                        .with(
                                            p().withClass(CLS_PKIOK)
                                                .withText("Was decrypted using Key ")
                                                .with(
                                                    b(
                                                        element
                                                            .getFacetOrFail(RbelVauErpFacet.class)
                                                            .getKeyIdUsed()
                                                            .getRawStringContent())))
                                        .with(addNotes(element)))));
          }
        });
  }

  private final RbelElement message;
  private final RbelElement encryptedMessage;
  private final RbelElement requestId;
  private final RbelElement pVersionNumber;
  private final RbelElement keyIdUsed;
  private final RbelElement responseKey;
  private final RbelElement decryptedPString;
  @Builder.Default private final Optional<RbelKey> keyUsed = Optional.empty();

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .withSkipIfNull("message", message)
        .withSkipIfNull("encryptedMessage", encryptedMessage)
        .withSkipIfNull("requestId", requestId)
        .withSkipIfNull("pVersionNumber", pVersionNumber)
        .withSkipIfNull("keyId", keyIdUsed)
        .withSkipIfNull("responseKey", responseKey)
        .withSkipIfNull("decryptedPString", decryptedPString);
  }
}
