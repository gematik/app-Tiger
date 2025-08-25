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
package de.gematik.rbellogger.facets.mime;

import static j2html.TagCreator.*;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelLazyValueFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.specialized.H2Tag;
import java.util.Optional;
import java.util.function.Supplier;

public class RbelMimeBodyFacet extends RbelLazyValueFacet<String> {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelMimeBodyFacet.class);
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
            H2Tag title = h2().withClass("title").withText("Mime Body: ");
            if (allRootFacetsAreMimeBody(element)) {
              return div(title, renderingToolkit.renderValueAsTextArea(element));
            } else {
              return title;
            }
          }

          private static boolean allRootFacetsAreMimeBody(RbelElement element) {
            return element.getFacets().stream()
                .filter(RbelRootFacet.class::isInstance)
                .map(RbelRootFacet.class::cast)
                .map(RbelRootFacet::getRootFacet)
                .allMatch(RbelMimeBodyFacet.class::isInstance);
          }
        });
  }

  public RbelMimeBodyFacet(Supplier<String> value) {
    super(value);
  }
}
