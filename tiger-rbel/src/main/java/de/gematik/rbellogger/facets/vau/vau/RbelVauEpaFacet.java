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
package de.gematik.rbellogger.facets.vau.vau;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.b;
import static j2html.TagCreator.div;
import static j2html.TagCreator.p;
import static j2html.TagCreator.span;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelVauKey;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Builder
@Getter
public class RbelVauEpaFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelVauEpaFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            RbelElement vauMessage = element.getFacetOrFail(RbelVauEpaFacet.class).getMessage();
            return div(t1ms("VAU Encrypted Message (EPA)")
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
                                                    element.getFacetOrFail(RbelVauEpaFacet.class))
                                                .map(RbelVauEpaFacet::getAdditionalHeaders)
                                                .map(renderingToolkit::convert)
                                                .orElse(span()),
                                            Optional.ofNullable(
                                                    element
                                                        .getFacetOrFail(RbelVauEpaFacet.class)
                                                        .getPVersionNumber())
                                                .map(
                                                    v ->
                                                        p(b("Version Number: "))
                                                            .withText(
                                                                v.seekValue().get().toString()))
                                                .map(DomContent.class::cast)
                                                .orElse(span()),
                                            Optional.ofNullable(
                                                    element
                                                        .getFacetOrFail(RbelVauEpaFacet.class)
                                                        .getSequenceNumber())
                                                .map(
                                                    v ->
                                                        p(b("Sequence Number: "))
                                                            .withText(
                                                                v.seekValue().get().toString()))
                                                .map(DomContent.class::cast)
                                                .orElse(span()),
                                            Optional.ofNullable(
                                                    element
                                                        .getFacetOrFail(RbelVauEpaFacet.class)
                                                        .getPHeaderInformation())
                                                .map(
                                                    v ->
                                                        p(b("P Header (raw): "))
                                                            .withText(
                                                                v.seekValue().get().toString()))
                                                .map(DomContent.class::cast)
                                                .orElse(span())),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(
                                            t2("Body")
                                                .with(
                                                    showContentButtonAndDialog(
                                                        vauMessage, renderingToolkit))
                                                .with(addNotes(vauMessage)))
                                        .with(renderingToolkit.convert(vauMessage)),
                                    childBoxNotifTitle(CLS_PKIOK)
                                        .with(
                                            p().withClass(CLS_PKIOK)
                                                .withText("Was decrypted using Key ")
                                                .with(
                                                    b(
                                                        element
                                                            .getFacetOrFail(RbelVauEpaFacet.class)
                                                            .getKeyIdUsed()
                                                            .getRawStringContent()),
                                                    element
                                                        .getFacet(RbelVauEpaFacet.class)
                                                        .flatMap(RbelVauEpaFacet::getKeyUsed)
                                                        .filter(RbelVauKey.class::isInstance)
                                                        .map(RbelVauKey.class::cast)
                                                        .map(
                                                            vauKey ->
                                                                vauKey.getParentKey().getKeyName())
                                                        .map(
                                                            parentKeys ->
                                                                span(" derived from ")
                                                                    .with(b(parentKeys)))
                                                        .orElse(span())))
                                        .with(addNotes(element)))));
          }
        });
  }

  private final RbelElement message;
  private final RbelElement encryptedMessage;
  private final RbelElement sequenceNumber;
  private final RbelElement additionalHeaders;
  private final RbelElement pVersionNumber;
  private final RbelElement keyIdUsed;
  private final RbelElement pHeaderInformation;
  private final RbelElement decryptedHeader;
  @Builder.Default private final Optional<RbelKey> keyUsed = Optional.empty();

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .withSkipIfNull("message", message)
        .withSkipIfNull("encryptedMessage", encryptedMessage)
        .withSkipIfNull("additionalHeaders", additionalHeaders)
        .withSkipIfNull("sequenceNumber", sequenceNumber)
        .withSkipIfNull("pVersionNumber", pVersionNumber)
        .withSkipIfNull("pHeaderInformation", pHeaderInformation)
        .withSkipIfNull("keyId", keyIdUsed)
        .withSkipIfNull("decryptedHeader", decryptedHeader);
  }
}
