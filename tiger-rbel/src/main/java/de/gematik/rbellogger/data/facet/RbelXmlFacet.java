/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.JsonNoteEntry;
import j2html.tags.ContainerTag;
import j2html.tags.Text;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RbelXmlFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelXmlFacet.class)
                && element
                    .getFacet(RbelRootFacet.class)
                    .filter(root -> root.getRootFacet() instanceof RbelXmlFacet)
                    .isPresent();
          }

          @SuppressWarnings({"rawtypes", "java:S3740"})
          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            String formattedXml =
                RbelHtmlRenderingToolkit.prettyPrintXml(element.getRawStringContent());
            for (final Entry<UUID, JsonNoteEntry> entry :
                renderingToolkit.getNoteTags().entrySet()) {
              if (formattedXml.contains(entry.getValue().getStringToMatch() + ",")) {
                formattedXml =
                    formattedXml.replace(
                        entry.getValue().getStringToMatch() + ",",
                        entry.getValue().getTagForKeyReplacement().render()
                            + ","
                            + entry.getValue().getTagForValueReplacement().render());
              } else if (formattedXml.contains(entry.getValue().getStringToMatch())) {
                formattedXml =
                    formattedXml.replace(
                        entry.getValue().getStringToMatch(),
                        entry.getValue().getTagForKeyReplacement().render()
                            + entry.getValue().getTagForValueReplacement().render());
              }
            }
            return ancestorTitle()
                .with(
                    vertParentTitle()
                        .with(
                            div()
                                .withClass("tile is-child pe-3")
                                .with(pre(new Text(formattedXml)).withClass("json language-xml"))
                                .with(renderingToolkit.convertNested(element))));
          }
        });
  }

  @Builder.Default private final RbelMultiMap<RbelElement> childElements = new RbelMultiMap<>();

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return childElements;
  }
}
