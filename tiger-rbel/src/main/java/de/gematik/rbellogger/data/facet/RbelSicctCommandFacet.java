/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.sicct.SicctMessageType;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import lombok.Builder;
import lombok.Data;

import java.util.Optional;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.*;

@Data
@Builder
public class RbelSicctCommandFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelSicctCommandFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                                                 RbelHtmlRenderingToolkit renderingToolkit) {
                final var sicctCommand = element.getFacetOrFail(RbelSicctCommandFacet.class);
                return div(t1ms("SICCT Command")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                            vertParentTitle().with(
                                childBoxNotifTitle(CLS_HEADER).with(t2("Header"))
                                    .with(renderingToolkit.convert(sicctCommand.getHeader())),
                                childBoxNotifTitle(CLS_BODY).with(t2("Body"))
                                    .with(renderingToolkit.printAsBinary(sicctCommand.getBody())))
                          )
                    );
            }
        });
    }

    private RbelElement header;
    private RbelElement body;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("header", header)
            .with("body", body);
    }
}
