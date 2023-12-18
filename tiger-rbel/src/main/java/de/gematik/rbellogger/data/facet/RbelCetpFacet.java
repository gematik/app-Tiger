/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class RbelCetpFacet extends RbelResponseFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelCetpFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return div()
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(t2("Message Length"))
                                        .with(
                                            addNotes(
                                                element
                                                    .getFacetOrFail(RbelCetpFacet.class)
                                                    .getMessageLength()))
                                        .with(
                                            renderingToolkit.convert(
                                                element
                                                    .getFacetOrFail(RbelCetpFacet.class)
                                                    .getMessageLength())),
                                    childBoxNotifTitle(CLS_BODY)
                                        .with(t2("Body"))
                                        .with(
                                            addNotes(
                                                element
                                                    .getFacetOrFail(RbelCetpFacet.class)
                                                    .getBody()))
                                        .with(
                                            renderingToolkit.convert(
                                                element
                                                    .getFacetOrFail(RbelCetpFacet.class)
                                                    .getBody())))));
          }
        });
  }

  private final RbelElement messageLength;
  private final RbelElement body;

  public RbelCetpFacet(RbelElement messageLength, RbelElement body) {
    super("CETP");
    this.body = body;
    this.messageLength = messageLength;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("messageLength", messageLength).with("body", body);
  }
}
