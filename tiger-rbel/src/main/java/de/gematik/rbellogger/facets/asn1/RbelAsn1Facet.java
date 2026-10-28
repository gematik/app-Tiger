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
import static j2html.TagCreator.each;
import static j2html.TagCreator.pre;
import static j2html.TagCreator.span;
import static j2html.TagCreator.text;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.facets.asn1.RbelAsn1TreePrinter.Line;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import de.gematik.rbellogger.util.RbelContent;
import j2html.tags.ContainerTag;
import j2html.tags.DomContent;
import java.io.IOException;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;

/**
 * A BER encoded data object, or a series of them. Children of constructed data objects are keyed by
 * their tag in hex if they are context-specific, application or private tagged (e.g. {@code A0},
 * {@code 7F21}), and by their position otherwise (e.g. {@code 0}, {@code 1}).
 */
@Value
public class RbelAsn1Facet implements RbelFacet {

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
                prettyPrint(element),
                br(),
                ancestorTitle()
                    .with(vertParentTitle().with(renderingToolkit.convertNested(element))));
          }

          private DomContent prettyPrint(RbelElement element) {
            return pre(each(RbelAsn1TreePrinter.print(element), this::renderLine))
                .withClass("binary")
                .withStyle("white-space: pre; overflow-x: auto;");
          }

          private DomContent renderLine(Line line) {
            return span(
                text("  ".repeat(line.depth())),
                span(line.key().isEmpty() ? "" : line.key() + "  ").withStyle("opacity: 0.6;"),
                text(line.type()),
                span(line.value().isEmpty() ? "" : "  " + line.value())
                    .withStyle("font-weight: bold;"),
                text("\n"));
          }

          @Override
          public int order() {
            return 10000;
          }
        });
  }

  /** The tag in hex, e.g. {@code 30} or {@code 7F21}; null for a series of data objects. */
  String tag;

  RbelMultiMap<RbelElement> childElements;

  /** The encoding of this data object; a slice of the original bytes, never re-encoded. */
  @ToString.Exclude @EqualsAndHashCode.Exclude RbelContent encoding;

  @Getter(lazy = true)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  ASN1Encodable asn1Content = decode();

  private ASN1Encodable decode() {
    if (tag == null) {
      return null;
    }
    try {
      return ASN1Primitive.fromByteArray(encoding.toByteArray());
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }
}
