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

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.util.Optional;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;

@Data
@Builder
public class RbelSicctEnvelopeFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelSicctEnvelopeFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                                                 RbelHtmlRenderingToolkit context) {
                final var sicctEnv = element.getFacetOrFail(RbelSicctEnvelopeFacet.class);
                return div(t1ms("SICCT Envelope")
                    .with(showContentButtonAndDialog(element, context)))
                    .withStyle("width:100%;")
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                            vertParentTitle().with(
                                childBoxNotifTitle(CLS_HEADER).with(t2("Envelope"))
                                    .with(context.packAsInfoLine("Message Type", context.formatHexAlike(
                                            sicctEnv.getMessageType().seekValue().orElse("").toString())))
                                    .with(context.packAsInfoLine("SrcOrDesaddress",
                                        context.formatHex(sicctEnv.getSrcOrDesAddress())))
                                    .with(context.packAsInfoLine("Reserved for future use",
                                        context.formatHex(sicctEnv.getAbRfu())))
                                    .with(context.packAsInfoLine("Length",
                                        context.formatHex(sicctEnv.getLength()),
                                        text(" (" + new BigInteger(sicctEnv.getLength().getRawContent()) + " bytes)"))),
                                childBoxNotifTitle(CLS_BODY).with(t2("Body"))
                                    .with(context.convert(sicctEnv.getCommand()))
                            )
                        )
                    );
            }
        });
    }

    private RbelElement messageType;
    private RbelElement srcOrDesAddress;
    private RbelElement sequenceNumber;
    private RbelElement abRfu;
    private RbelElement length;
    private RbelElement command;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("messageType", messageType)
            .with("srcOrDesAddress", srcOrDesAddress)
            .with("sequenceNumber", sequenceNumber)
            .with("abRfu", abRfu)
            .with("length", length)
            .with("command", command);
    }
}
