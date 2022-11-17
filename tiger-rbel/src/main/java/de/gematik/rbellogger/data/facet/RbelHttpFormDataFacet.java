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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

@Data
@Builder(toBuilder = true)
public class RbelHttpFormDataFacet implements RbelFacet {
    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelHttpFormDataFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                RbelHtmlRenderingToolkit renderingToolkit) {
                return table()
                    .withClass("table").with(
                        thead(
                            tr(th("name"), th("value"))
                        ),
                        tbody().with(
                            element.getFacetOrFail(RbelHttpFormDataFacet.class).getChildElements().stream()
                                .map(entry ->
                                    tr(
                                        td(pre(entry.getKey())),
                                        td(pre()
                                            .with(renderingToolkit.convert(entry.getValue(),
                                                Optional.ofNullable(entry.getKey())))
                                            .withClass("value"))
                                            .with(renderingToolkit.addNotes(entry.getValue()))
                                    )
                                )
                                .collect(Collectors.toList())
                        )
                    );
            }
        });
    }

    private final RbelMultiMap formDataMap;

    @Override
         public RbelMultiMap getChildElements() {
            return formDataMap;
        }
}
