/*
 *
 * Copyright 2021-2025 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.facets.jackson;

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
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;

@Value
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
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            String json;
            try {
              json =
                  renderingToolkit
                      .getObjectMapper()
                      .writeValueAsString(
                          renderingToolkit.shadeJson(
                              element.getFacetOrFail(RbelCborFacet.class).node,
                              Optional.empty(),
                              element));
            } catch (Exception e) {
              json = element.getRawStringContent();
            }
            String formattedJson = renderingToolkit.replaceNoteTags(json);

            return div(
                t1ms("CBOR").with(showContentButtonAndDialog(element, renderingToolkit)),
                pre(new UnescapedText(formattedJson)).withClass("binary"),
                br(),
                ancestorTitle()
                    .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }
        });
  }

  RbelElement unparsedBytes;
  JsonNode node;

  @Getter(lazy = true)
  RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>().with("unparsedBytes", unparsedBytes);
}
