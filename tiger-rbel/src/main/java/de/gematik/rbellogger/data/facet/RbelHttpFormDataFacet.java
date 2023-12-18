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
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RbelHttpFormDataFacet implements RbelFacet {
  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelHttpFormDataFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return table()
                .withClass("table")
                .with(
                    thead(tr(th("name"), th("value"))),
                    tbody()
                        .with(
                            element
                                .getFacetOrFail(RbelHttpFormDataFacet.class)
                                .getChildElements()
                                .stream()
                                .map(
                                    entry ->
                                        tr(
                                            td(pre(entry.getKey())),
                                            td(pre()
                                                    .with(
                                                        renderingToolkit.convert(
                                                            entry.getValue(),
                                                            Optional.ofNullable(entry.getKey())))
                                                    .withClass("value"))
                                                .with(
                                                    RbelHtmlRenderingToolkit.addNotes(
                                                        entry.getValue()))))
                                .toList()));
          }
        });
  }

  private final RbelMultiMap<RbelElement> formDataMap;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return formDataMap;
  }
}
