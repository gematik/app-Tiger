/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.sicct.RbelSicctCommand;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class RbelSicctHeaderFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelSicctHeaderFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final var sicctHeader = element.getFacetOrFail(RbelSicctHeaderFacet.class);
            return div(t1ms("SICCT Command Header")
                    .with(showContentButtonAndDialog(element, renderingToolkit)))
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("Command"))
                                        .with(
                                            renderingToolkit.packAsInfoLine(
                                                "cla / ins",
                                                renderingToolkit.formatHex(sicctHeader.getCla()),
                                                renderingToolkit.formatHex(sicctHeader.getIns())))
                                        .with(
                                            renderingToolkit.packAsInfoLine(
                                                "p1 / p2",
                                                renderingToolkit.formatHex(sicctHeader.getP1()),
                                                renderingToolkit.formatHex(sicctHeader.getP2())))
                                        .with(
                                            renderingToolkit.packAsInfoLine(
                                                "SICCT Command",
                                                renderingToolkit.formatHexAlike(
                                                    Optional.ofNullable(sicctHeader.getCommand())
                                                        .map(RbelSicctCommand::name)
                                                        .orElse("<unknown>")))))));
          }
        });
  }

  private RbelElement cla;
  private RbelElement ins;
  private RbelElement p1;
  private RbelElement p2;
  private RbelSicctCommand command;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("cla", cla)
        .with("ins", ins)
        .with("p1", p1)
        .with("p2", p2);
  }
}
