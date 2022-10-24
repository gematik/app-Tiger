/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;

import java.util.List;
import java.util.Optional;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class RbelJwtFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelJwtFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                                                 RbelHtmlRenderingToolkit renderingToolkit) {
                return div(t1ms("JWT")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                            vertParentTitle().with(
                                childBoxNotifTitle(CLS_HEADER).with(t2("Headers"))
                                    .with(addNotes(element.getFacetOrFail(RbelJwtFacet.class).getHeader()))
                                    .with(renderingToolkit.convert(element.getFacetOrFail(RbelJwtFacet.class).getHeader())),
                                childBoxNotifTitle(CLS_BODY).with(t2("Body"))
                                    .with(addNotes(element.getFacetOrFail(RbelJwtFacet.class).getBody()))
                                    .with(renderingToolkit.convert(element.getFacetOrFail(RbelJwtFacet.class).getBody())),
                                renderingToolkit.convert(element.getFacetOrFail(RbelJwtFacet.class).getSignature())
                            )
                        )
                    );
            }
        });
    }

    private final RbelElement header;
    private final RbelElement body;
    private final RbelElement signature;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("header", header)
            .with("body", body)
            .with("signature", signature);
    }
}
