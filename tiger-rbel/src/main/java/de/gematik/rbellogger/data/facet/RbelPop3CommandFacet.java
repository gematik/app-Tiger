/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RbelPop3CommandFacet implements RbelFacet {

  private RbelElement command;
  @Nullable private RbelElement arguments;

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelPop3CommandFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelPop3CommandFacet facet = element.getFacetOrFail(RbelPop3CommandFacet.class);
            return div(
                h2().withClass("title").withText("POP3 Request"),
                p().with(b().withText("Command: "))
                    .withText(facet.getCommand().printValue().orElse("")),
                p().with(b().withText("Arguments: "))
                    .withText(
                        Optional.ofNullable(facet.getArguments())
                            .map(RbelElement::getRawStringContent)
                            .orElse("")),
                br());
          }
        });
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("pop3Command", command)
        .withSkipIfNull("pop3Arguments", arguments);
  }
}
