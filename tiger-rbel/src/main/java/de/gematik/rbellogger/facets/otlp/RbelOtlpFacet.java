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
package de.gematik.rbellogger.facets.otlp;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.CLS_BODY;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.addNotes;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.childBoxNotifTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t1ms;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t2;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.div;
import static j2html.TagCreator.span;

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

/** RBEL facet that exposes decoded OTLP payloads for rendering and traversal. */
@Data
@Builder
public class RbelOtlpFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelOtlpFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            var facet = element.getFacetOrFail(RbelOtlpFacet.class);
            var signalLabel =
                Optional.ofNullable(facet.getSignal())
                    .map(RbelOtlpSignal::getDisplayName)
                    .orElse("unknown");
            var contentType = Optional.ofNullable(facet.getContentType()).orElse("unknown");
            var path = Optional.ofNullable(facet.getPath()).orElse("unknown");

            return div(t1ms("OTLP " + signalLabel + " payload"))
                .with(addNotes(element, "mb-5"))
                .with(ancestorTitle()
                    .with(vertParentTitle()
                        .with(childBoxNotifTitle(CLS_BODY)
                                .with(t2("Signal"), span(signalLabel)),
                            childBoxNotifTitle(CLS_BODY)
                                .with(t2("Content Type"), span(contentType)),
                            childBoxNotifTitle(CLS_BODY).with(t2("Path"), span(path)),
                            childBoxNotifTitle(CLS_BODY)
                                .with(t2("Decoded Payload"))
                                .with(renderingToolkit.convert(facet.getDecoded())))));
          }
        });
  }

  private final RbelElement decoded;
  private final RbelOtlpSignal signal;
  private final String contentType;
  private final String path;

  /** Returns the decoded OTLP payload for RBEL tree traversal. */
  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with("decoded", decoded);
  }
}
