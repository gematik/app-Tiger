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

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.b;
import static j2html.TagCreator.div;
import static j2html.TagCreator.p;
import static j2html.TagCreator.span;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import j2html.tags.DomContent;
import lombok.Builder;
import lombok.Data;

@Builder(toBuilder = true)
@Data
public class RbelVauErpFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelVauErpFacet.class);
            }

            @Override
            public ContainerTag performRendering(final RbelElement element, final Optional<String> key,
                                                 final RbelHtmlRenderingToolkit renderingToolkit) {
                return div(t1ms("VAU Encrypted Message (E-Rezept)")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                        vertParentTitle().with(
                            childBoxNotifTitle(CLS_BODY).with(
                                t2("Header"),
                                Optional.ofNullable(element.getFacetOrFail(RbelVauErpFacet.class).getPVersionNumber())
                                    .map(v -> p(b("Version Number: ")).withText(v.getRawStringContent()))
                                    .map(DomContent.class::cast)
                                    .orElse(span()),
                                Optional.ofNullable(element.getFacetOrFail(RbelVauErpFacet.class).getRequestId())
                                    .map(v -> p(b("Request ID: ")).withText(v.getRawStringContent()))
                                    .map(DomContent.class::cast)
                                    .orElse(span())
                            ),
                            childBoxNotifTitle(CLS_BODY).with(t2("Body"))
                                .with(addNotes(element.getFacetOrFail(RbelVauErpFacet.class).getMessage()))
                                .with(renderingToolkit.convert(element.getFacetOrFail(RbelVauErpFacet.class).getMessage(), Optional.empty())),
                            childBoxNotifTitle(CLS_PKIOK).with(
                                    p()
                                        .withClass(CLS_PKIOK)
                                        .withText("Was decrypted using Key ")
                                        .with(b(element.getFacetOrFail(RbelVauErpFacet.class).getKeyIdUsed().getRawStringContent())))
                                .with(addNotes(element))
                        )));
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
    @Builder.Default
    private final Optional<RbelKey> keyUsed = Optional.empty();

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .withSkipIfNull("message", message)
            .withSkipIfNull("encryptedMessage", encryptedMessage)
            .withSkipIfNull("requestId", requestId)
            .withSkipIfNull("pVersionNumber", pVersionNumber)
            .withSkipIfNull("keyId", keyIdUsed)
            .withSkipIfNull("responseKey", responseKey)
            .withSkipIfNull("decryptedPString", decryptedPString);
    }
}
