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
package de.gematik.rbellogger.facets.uri;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.addNotes;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.ancestorTitle;
import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.vertParentTitle;
import static j2html.TagCreator.br;
import static j2html.TagCreator.div;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import j2html.tags.UnescapedText;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class RbelUriFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelUriFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            final RbelUriFacet uriFacet = element.getFacetOrFail(RbelUriFacet.class);
            final String originalUrl = element.getRawStringContent();
            final ContainerTag urlContent =
                renderUrlContent(renderingToolkit, uriFacet, originalUrl);
            if (element.traverseAndReturnNestedMembers().isEmpty()) {
              return div().with(urlContent).with(addNotes(element));
            } else {
              return ancestorTitle()
                  .with(
                      vertParentTitle()
                          .with(
                              div()
                                  .withClass("tile is-child pe-2")
                                  .with(urlContent)
                                  .with(addNotes(element))
                                  .with(renderingToolkit.convertNested(element))));
            }
          }

          @SuppressWarnings({"rawtypes", "java:S3740"})
          private ContainerTag renderUrlContent(
              RbelHtmlRenderingToolkit renderingToolkit,
              RbelUriFacet uriFacet,
              String originalUrl) {
            if (!originalUrl.contains("?")) {
              return div(new UnescapedText(originalUrl));
            } else {
              final ContainerTag div = div(uriFacet.getBasicPathString() + "?").with(br());
              boolean firstElement = true;
              for (final RbelElement queryElementEntry : uriFacet.getQueryParameters()) {
                final RbelUriParameterFacet parameterFacet =
                    queryElementEntry.getFacetOrFail(RbelUriParameterFacet.class);
                final String shadedStringContent =
                    renderingToolkit
                        .shadeValue(
                            parameterFacet.getValue(), Optional.of(parameterFacet.getKeyAsString()))
                        .map(content -> queryElementEntry.getKey() + "=" + content)
                        .orElse(queryElementEntry.getRawStringContent());

                div.with(
                    div((firstElement ? "" : "&") + shadedStringContent)
                        .with(addNotes(queryElementEntry, " ms-6")));
                firstElement = false;
              }
              return div;
            }
          }
        });
  }

  private final RbelElement basicPath;
  private final List<RbelElement> queryParameters;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    RbelMultiMap<RbelElement> result = new RbelMultiMap<>();
    queryParameters.forEach(
        el -> result.put(el.getFacetOrFail(RbelUriParameterFacet.class).getKeyAsString(), el));
    result.put("basicPath", basicPath);
    return result;
  }

  public String getBasicPathString() {
    return basicPath.seekValue(String.class).orElseThrow();
  }

  public Optional<RbelElement> getQueryParameter(String key) {
    Objects.requireNonNull(key);
    return queryParameters.stream()
        .map(element -> element.getFacetOrFail(RbelUriParameterFacet.class))
        .filter(e -> e.getKeyAsString().equals(key))
        .map(RbelUriParameterFacet::getValue)
        .findFirst();
  }
}
