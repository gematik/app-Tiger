/*
 * Copyright (c) 2023 gematik GmbH
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
import lombok.EqualsAndHashCode;

import java.util.Optional;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class RbelCetpFacet extends RbelResponseFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelCetpFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit renderingToolkit) {
                return div()
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(vertParentTitle()
                        .with(childBoxNotifTitle(CLS_HEADER).with(t2("Message Length"))
                                .with(addNotes(element.getFacetOrFail(RbelCetpFacet.class).getMessageLength()))
                                .with(renderingToolkit.convert(
                                    element.getFacetOrFail(RbelCetpFacet.class).getMessageLength())),
                            childBoxNotifTitle(CLS_BODY).with(t2("Body"))
                                .with(addNotes(element.getFacetOrFail(RbelCetpFacet.class).getBody()))
                                .with(
                                    renderingToolkit.convert(element.getFacetOrFail(RbelCetpFacet.class).getBody())))));
            }
        });
    }

    private final RbelElement messageLength;
    private final RbelElement body;

    public RbelCetpFacet(RbelElement messageLength, RbelElement body) {
        super("CETP");
        this.body = body;
        this.messageLength = messageLength;
    }

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("messageLength", messageLength)
            .with("body", body);
    }
}
