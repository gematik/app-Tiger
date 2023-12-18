/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.vau;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

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
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return ancestorTitle()
                .with(
                    vertParentTitle()
                        .with(
                            childBoxNotifTitle(CLS_BODY)
                                .with(t2("VAU Session Information"))
                                .with(
                                    div(
                                        "Record-ID: "
                                            + element
                                                .getFacetOrFail(VauSessionFacet.class)
                                                .getRecordId()
                                                .getRawStringContent()))));
          }

          @Override
          public int order() {
            return 100;
          }
        });
  }

  private final RbelElement recordId;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("recordId", recordId);
  }
}
