/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
