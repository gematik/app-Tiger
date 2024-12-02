package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.addNotes;
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
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
public class RbelLdapFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelLdapFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelElement ldapMessage =
                element
                    .getFacetOrFail(RbelLdapFacet.class)
                    .getChildElements()
                    .get("textRepresentation");

            return div(t1ms("LDAP").with(showContentButtonAndDialog(element, renderingToolkit)))
                .with(addNotes(element, "mb-5"))
                .with(
                    ancestorTitle()
                        .with(
                            vertParentTitle()
                                .with(
                                    childBoxNotifTitle(CLS_HEADER)
                                        .with(
                                            t2("Message")
                                                .with(
                                                    showContentButtonAndDialog(
                                                        ldapMessage, renderingToolkit))
                                                .with(addNotes(ldapMessage))
                                                .with(renderingToolkit.convert(ldapMessage))))));
          }
        });
  }

  private final RbelElement textRepresentation;
  private final RbelElement msgId;
  private final RbelElement protocolOp;
  private final RbelElement attributes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("textRepresentation", textRepresentation)
        .with("msgId", msgId)
        .with("protocolOp", protocolOp)
        .with("attributes", attributes);
  }
}
