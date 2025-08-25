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
package de.gematik.rbellogger.facets.pki;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class RbelPkcs7Facet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelPkcs7Facet.class);
          }

          @Override
          public boolean shouldRenderLargeElements() {
            return true;
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            RbelPkcs7Facet pkcs7Facet = element.getFacetOrFail(RbelPkcs7Facet.class);
            var signerInfos = pkcs7Facet.getSignerInfos();
            var renderedSignerInfos =
                signerInfos.getFacetOrFail(RbelListFacet.class).getChildElements().stream()
                    .map(signerInfo -> renderingToolkit.convert(signerInfo.getValue()))
                    .toList();
            return div(
                h2().withClass("title").withText("PKCS#7 Signed Message: "),
                vertParentTitle().with(renderedSignerInfos),
                renderingToolkit.convert(pkcs7Facet.getSigned()));
          }
        });
  }

  private final RbelElement signed;
  private final RbelElement signerInfos;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("signed", signed).with("signerInfos", signerInfos);
  }
}
