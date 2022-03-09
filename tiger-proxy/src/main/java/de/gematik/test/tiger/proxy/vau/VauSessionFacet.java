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

package de.gematik.test.tiger.proxy.vau;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.data.facet.RbelHttpMessageFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

@Data
@Builder(toBuilder = true)
public class VauSessionFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(
            new RbelHtmlFacetRenderer() {
                @Override
                public boolean checkForRendering(RbelElement element) {
                    return element.hasFacet(VauSessionFacet.class);
                }

                @Override
                public ContainerTag performRendering(RbelElement element, Optional<String> key,
                    RbelHtmlRenderingToolkit renderingToolkit) {
                    return ancestorTitle().with(
                        vertParentTitle().with(
                            childBoxNotifTitle(CLS_BODY).with(t2("VAU Session Information"))
                                .with(div("Record-ID: "
                                    + element.getFacetOrFail(VauSessionFacet.class)
                                    .getRecordId().getRawStringContent()))
                        )
                    );
                }

                @Override
                public int order() {
                    return 100;
                }
            }
        );
    }

    private final RbelElement recordId;

    @Override
    public List<Map.Entry<String, RbelElement>> getChildElements() {
        return List.of(
            Pair.of("recordId", recordId)
        );
    }
}
