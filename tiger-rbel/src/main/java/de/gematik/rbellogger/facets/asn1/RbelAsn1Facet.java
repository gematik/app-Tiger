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
package de.gematik.rbellogger.facets.asn1;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.br;
import static j2html.TagCreator.div;
import static j2html.TagCreator.pre;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.GenericPrettyPrinter;
import j2html.tags.ContainerTag;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;

@Data
@Builder(toBuilder = true)
@Slf4j
public class RbelAsn1Facet implements RbelFacet {

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
            return element.hasFacet(RbelAsn1Facet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return div(
                pre(ASN1_PRETTY_PRINTER.prettyPrint(
                        element.getFacetOrFail(RbelAsn1Facet.class).getAsn1Content()))
                    .withClass("binary"),
                br(),
                ancestorTitle()
                    .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }

          @Override
          public int order() {
            return 10000;
          }
        });
  }

  private final RbelElement unparsedBytes;
  private final ASN1Encodable asn1Content;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("unparsedBytes", unparsedBytes);
  }
}
