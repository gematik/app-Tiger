/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;
import static j2html.TagCreator.p;
import static j2html.TagCreator.span;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.util.MtomPart;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;

import j2html.tags.DomContent;
import java.util.List;
import java.util.Optional;

import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Data;

@Data
public class RbelMtomFacet implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelMtomFacet.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                RbelHtmlRenderingToolkit renderingToolkit) {
                return div(
                    t1ms("XML XOP/MTOM Message")
                        .with(showContentButtonAndDialog(element, renderingToolkit)))
                    .with(addNotes(element, "mb-5"))
                    .with(ancestorTitle().with(
                            vertParentTitle().with(
                                    childBoxNotifTitle(CLS_BODY).with(
                                        t2("Content Type"),
                                        Optional.ofNullable(element.getFacetOrFail(RbelMtomFacet.class))
                                            .map(RbelMtomFacet::getContentType)
                                            .map(renderingToolkit::convert)
                                            .orElse(span())
                                    ),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(t2("Reconstructed Message"))
                                        .with(addNotes(element.getFacetOrFail(RbelMtomFacet.class).getReconstructedMessage()))
                                        .with(renderingToolkit
                                            .convert(element.getFacetOrFail(RbelMtomFacet.class).getReconstructedMessage()))
                                )
                                .with(convertDataParts(element.getFacetOrFail(RbelMtomFacet.class), renderingToolkit))
                        )
                    );
            }

            private List<DomContent> convertDataParts(RbelMtomFacet mtomFacet, RbelHtmlRenderingToolkit renderingToolkit) {
                if (mtomFacet.getDataParts() == null
                    || mtomFacet.getDataParts().getFacet(RbelListFacet.class)
                    .map(RbelListFacet::isEmpty)
                    .orElse(false)) {
                    return List.of();
                }
                return mtomFacet.getDataParts().getFacetOrFail(RbelListFacet.class)
                    .getChildNodes().stream()
                    .map(dataNode -> childBoxNotifTitle(CLS_BODY)
                        .with(t2("Data Part "))
                        .with(p(dataNode.getFacetOrFail(RbelMtomDataPartFacet.class).getXpath().printValue().orElse("<>")))
                        .with(showContentButtonAndDialog(dataNode.getFacetOrFail(RbelMtomDataPartFacet.class).getContent(), renderingToolkit))
                        .with(addNotes(dataNode.getFacetOrFail(RbelMtomDataPartFacet.class).getContent()))
                        .with(renderingToolkit.convert(dataNode.getFacetOrFail(RbelMtomDataPartFacet.class).getContent())))
                    .collect(Collectors.toList());
            }
        });
    }

    private final RbelElement contentType;
    private final RbelElement reconstructedMessage;
    @Nullable
    private final RbelElement dataParts;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("contentType", contentType)
            .with("reconstructedMessage", reconstructedMessage)
            .withSkipIfNull("dataParts", dataParts);
    }
}
