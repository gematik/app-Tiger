/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderer.showContentButtonAndDialog;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t1ms;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.br;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.JsonNoteEntry;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;

@Data
@Builder(toBuilder = true)
public class RbelCborFacet implements RbelFacet {

  private static final GenericPrettyPrinter<ASN1Encodable> ASN1_PRETTY_PRINTER =
      new GenericPrettyPrinter<>(
          asn1 -> !((asn1 instanceof ASN1Sequence) || (asn1 instanceof ASN1Set)),
          Object::toString,
          asn1 -> StreamSupport.stream(((Iterable<ASN1Encodable>) asn1).spliterator(), false));

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelCborFacet.class);
          }

          @Override
          @SneakyThrows
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            String formatedJson =
                renderingToolkit
                    .getObjectMapper()
                    .writeValueAsString(
                        renderingToolkit.shadeJson(
                            element.getFacetOrFail(RbelCborFacet.class).node,
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

            return div(
                t1ms("CBOR").with(showContentButtonAndDialog(element, renderingToolkit)),
                pre(new UnescapedText(formatedJson)).withClass("binary"),
                br(),
                ancestorTitle()
                    .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }
        });
  }

  private final RbelElement unparsedBytes;
  private final JsonNode node;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("unparsedBytes", unparsedBytes);
  }
}
