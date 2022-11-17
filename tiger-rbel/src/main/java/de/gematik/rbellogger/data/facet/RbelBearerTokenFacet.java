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
import java.util.List;
import java.util.Optional;

import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;

@RequiredArgsConstructor
@Data
public class RbelBearerTokenFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelBearerTokenFacet.class);
            }

            @Override
            public ContainerTag performRendering(final RbelElement element, final Optional<String> key,
                                                 final RbelHtmlRenderingToolkit renderingToolkit) {
                return div(t1ms("Bearer Token")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                        vertParentTitle().with(
                            childBoxNotifTitle(CLS_BODY))
                                .with(addNotes(element.getFacetOrFail(RbelBearerTokenFacet.class).getBearerToken()))
                                .with(renderingToolkit.convert(element.getFacetOrFail(RbelBearerTokenFacet.class).getBearerToken(), Optional.empty()))
                        ));
            }
        });
    }

    private final RbelElement bearerToken;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("BearerToken", bearerToken);
    }
}
