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
package de.gematik.rbellogger.facets.sicct;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.addNotes;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.math.BigInteger;
import java.util.Optional;
import lombok.Getter;
import lombok.Value;

/** An ECDSA signature in plain format according to TR-03111: r and s of equal length, r || s. */
@Value
public class RbelEcdsaPlainSignatureFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelEcdsaPlainSignatureFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit toolkit) {
            final var signature = element.getFacetOrFail(RbelEcdsaPlainSignatureFacet.class);
            return div()
                .with(addNotes(element))
                .with(toolkit.packAsInfoLine("r", toolkit.formatHex(signature.getR())))
                .with(toolkit.packAsInfoLine("s", toolkit.formatHex(signature.getS())));
          }
        });
  }

  RbelElement r;
  RbelElement s;

  public static RbelEcdsaPlainSignatureFacet of(RbelElement signature) {
    final int half = (int) signature.getSize() / 2;
    return new RbelEcdsaPlainSignatureFacet(
        integer(signature, 0, half), integer(signature, half, 2 * half));
  }

  private static RbelElement integer(RbelElement signature, int from, int to) {
    final byte[] bytes = signature.getContent().toByteArray(from, to);
    return RbelElement.wrap(bytes, signature, new BigInteger(1, bytes));
  }

  @Getter(lazy = true)
  RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>().with("r", r).with("s", s);
}
