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
import de.gematik.rbellogger.data.sicct.RbelSicctCommand;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.b;
import static j2html.TagCreator.div;

@Data
@Builder
@Slf4j
public class RbelSicctHeaderFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelSicctHeaderFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                                                 RbelHtmlRenderingToolkit renderingToolkit) {
                final var sicctHeader = element.getFacetOrFail(RbelSicctHeaderFacet.class);
                return div(t1ms("SICCT Command Header")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                            vertParentTitle().with(
                                childBoxNotifTitle(CLS_HEADER).with(t2("Command"))
                                    .with(renderingToolkit.packAsInfoLine("cla / ins",
                                            renderingToolkit.formatHex(sicctHeader.getCla()),
                                            renderingToolkit.formatHex(sicctHeader.getIns())))
                                    .with(renderingToolkit.packAsInfoLine("p1 / p2",
                                                renderingToolkit.formatHex(sicctHeader.getP1()),
                                                renderingToolkit.formatHex(sicctHeader.getP2())))
                                    .with(renderingToolkit.packAsInfoLine("SICCT Command",
                                            renderingToolkit.formatHexAlike(
                                                    Optional.ofNullable(sicctHeader.getCommand())
                                                    .map(RbelSicctCommand::name)
                                                    .orElse("<unknown>")))
                            ))
                        )
                    );
            }
        });
    }

    private RbelElement cla;
    private RbelElement ins;
    private RbelElement p1;
    private RbelElement p2;
    private RbelSicctCommand command;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("cla", cla)
            .with("ins", ins)
            .with("p1", p1)
            .with("p2", p2);
    }
}
