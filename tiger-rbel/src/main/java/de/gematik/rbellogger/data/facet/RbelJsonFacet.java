/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.JsonNoteEntry;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class RbelJsonFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelJsonFacet.class)
                && element
                    .getFacet(RbelRootFacet.class)
                    .filter(root -> root.getRootFacet() instanceof RbelJsonFacet)
                    .isPresent();
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            String formatedJson =
                renderingToolkit.GSON.toJson(
                    renderingToolkit.shadeJson(
                        JsonParser.parseString(element.getRawStringContent()),
                        Optional.empty(),
                        element));
            for (final Entry<UUID, JsonNoteEntry> entry :
                renderingToolkit.getNoteTags().entrySet()) {
              if (formatedJson.contains(entry.getValue().getStringToMatch() + ",")) {
                formatedJson =
                    formatedJson.replace(
                        entry.getValue().getStringToMatch() + ",",
                        entry.getValue().getTagForKeyReplacement().render()
                            + ","
                            + entry.getValue().getTagForValueReplacement().render());
              } else if (formatedJson.contains(entry.getValue().getStringToMatch())) {
                formatedJson =
                    formatedJson.replace(
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
                                .with(
                                    pre(new UnescapedText(formatedJson))
                                        .withClass("json language-json"))
                                .with(renderingToolkit.convertNested(element))));
          }
        });
  }

  private final JsonElement jsonElement;

  @Override
  public RbelMultiMap getChildElements() {
    return new RbelMultiMap();
  }
}
