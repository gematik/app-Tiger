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
package de.gematik.rbellogger.facets.http.formdata;

import static j2html.TagCreator.*;

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

@Data
@Builder(toBuilder = true)
public class RbelHttpFormDataFacet implements RbelFacet {
  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelHttpFormDataFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element,
              Optional<String> key,
              RbelHtmlRenderingToolkit renderingToolkit) {
            return table()
                .withClass("table")
                .with(
                    thead(tr(th("name"), th("value"))),
                    tbody()
                        .with(
                            element
                                .getFacetOrFail(RbelHttpFormDataFacet.class)
                                .getChildElements()
                                .stream()
                                .map(
                                    entry ->
                                        tr(
                                            td(pre(entry.getKey())),
                                            td(pre()
                                                    .with(
                                                        renderingToolkit.convert(
                                                            entry.getValue(),
                                                            Optional.ofNullable(entry.getKey())))
                                                    .withClass("value"))
                                                .with(
                                                    RbelHtmlRenderingToolkit.addNotes(
                                                        entry.getValue()))))
                                .toList()));
          }
        });
  }

  private final RbelMultiMap<RbelElement> formDataMap;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return formDataMap;
  }
}
