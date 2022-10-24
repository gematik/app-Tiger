/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.elements;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.b;
import static j2html.TagCreator.p;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
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
@RequiredArgsConstructor
@Builder
public class RbelJwtSignature implements RbelFacet {

    static {
        RbelHtmlRenderer.registerFacetRenderer(new RbelHtmlFacetRenderer() {
            @Override
            public boolean checkForRendering(RbelElement element) {
                return element.hasFacet(RbelJwtSignature.class);
            }

            @Override
            public ContainerTag performRendering(RbelElement element, Optional<String> key,
                                                 RbelHtmlRenderingToolkit renderingToolkit) {
                return
                    childBoxNotifTitle((element.getFacetOrFail(RbelJwtSignature.class).isValid()) ? CLS_PKIOK : CLS_PKINOK)
                        .with(t2("Signature"))
                        .with(addNotes(element))
                        .with(p()
                            .withText("Was verified using Key ")
                            .with(b(element.getFacetOrFail(RbelJwtSignature.class).wasVerifiedUsing()))
                        );
            }
        });
    }

    private final RbelElement isValid;
    private final RbelElement verifiedUsing;

    @Override
    public RbelMultiMap getChildElements() {
        return new RbelMultiMap()
            .with("isValid", isValid)
            .with("verifiedUsing", verifiedUsing);
    }

    public boolean isValid() {
        return isValid.seekValue(Boolean.class)
            .orElseThrow();
    }

    private String wasVerifiedUsing() {
        return Optional.ofNullable(verifiedUsing)
            .flatMap(verifiedUsing -> verifiedUsing.seekValue(String.class))
            .orElse("");
    }
}
