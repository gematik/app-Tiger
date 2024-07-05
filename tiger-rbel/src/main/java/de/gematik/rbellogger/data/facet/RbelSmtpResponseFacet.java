/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.util.EmailConversionUtils.CRLF;
import static j2html.TagCreator.b;
import static j2html.TagCreator.div;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.p;
import static j2html.TagCreator.pre;
import static j2html.TagCreator.span;
import static j2html.TagCreator.text;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.TagCreator;
import j2html.tags.ContainerTag;
import java.util.Arrays;
import java.util.Optional;
import javax.annotation.Nullable;

import j2html.tags.DomContent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RbelSmtpResponseFacet implements RbelFacet {

  private static final int MAX_RENDERED_RESPONSE_LINES = 10;

  private RbelElement status;
  @Nullable private RbelElement body;

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelSmtpResponseFacet.class);
          }

          @Override
          public ContainerTag<?> performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelSmtpResponseFacet facet = element.getFacetOrFail(RbelSmtpResponseFacet.class);
            var bodyContent =
                Optional.ofNullable(facet.getBody())
                    .map(RbelElement::getRawStringContent)
                    .filter(s -> renderingToolkit.shouldRenderEntitiesWithSize(s.length()))
                    .map(s -> s.split(CRLF))
                    .map(Arrays::stream)
                    .map(stream -> stream.map(s -> text(s + CRLF)).toList())
                    .map(pre()::with)
                    .map(o -> (DomContent)o)
                    .orElseGet(() -> computeReplacementString(facet.getBody()));

            return div(
                h2().withClass("title").withText("SMTP Response"),
                p().with(b().withText("Status: "))
                    .withText(facet.getStatus().printValue().orElse("")),
                p().with(b().withText("Body: ")).with(bodyContent));
          }
        });
  }

  private static DomContent computeReplacementString(RbelElement bodyElement) {
    return Optional.ofNullable(bodyElement)
        .map(body ->
            (DomContent)span(RbelHtmlRenderer.buildOversizeReplacementString(body))
                .withClass("is-size-7"))
        .orElseGet(TagCreator::pre);
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("status", status).withSkipIfNull("body", body);
  }
}
