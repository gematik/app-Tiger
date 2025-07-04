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
package de.gematik.rbellogger.facets.jose;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.*;
import static j2html.TagCreator.b;
import static j2html.TagCreator.p;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@Builder
public class RbelJwtSignature implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelJwtSignature.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return childBoxNotifTitle(
                    (element.getFacetOrFail(RbelJwtSignature.class).isValid())
                        ? CLS_PKIOK
                        : CLS_PKINOK)
                .with(t2("Signature"))
                .with(addNotes(element))
                .with(
                    p().withText("Was verified using Key ")
                        .with(
                            b(element.getFacetOrFail(RbelJwtSignature.class).wasVerifiedUsing())));
          }
        });
  }

  private final RbelElement isValid;
  private final RbelElement verifiedUsing;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>()
        .with("isValid", isValid)
        .with("verifiedUsing", verifiedUsing);
  }

  public boolean isValid() {
    return isValid.seekValue(Boolean.class).orElseThrow();
  }

  private String wasVerifiedUsing() {
    return Optional.ofNullable(verifiedUsing)
        .flatMap(verifiedUsingElement -> verifiedUsingElement.seekValue(String.class))
        .orElse("");
  }
}
