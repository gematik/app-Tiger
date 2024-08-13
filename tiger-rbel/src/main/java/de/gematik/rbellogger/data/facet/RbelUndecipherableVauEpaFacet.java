/*
 * Copyright 2024 gematik GmbH
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
 */

package de.gematik.rbellogger.data.facet;

import static de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit.t1ms;
import static j2html.TagCreator.div;
import static j2html.TagCreator.text;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.renderer.RbelHtmlFacetRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderingToolkit;
import j2html.tags.ContainerTag;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@Data
public class RbelUndecipherableVauEpaFacet implements RbelFacet {

  static {
    RbelHtmlRenderer.registerFacetRenderer(
        new RbelHtmlFacetRenderer() {
          @Override
          public boolean checkForRendering(RbelElement element) {
            return element.hasFacet(RbelUndecipherableVauEpaFacet.class)
                && element.getParentNode() != null
                && !element.getParentNode().hasFacet(RbelHttpHeaderFacet.class)
                && element.getParentNode().hasFacet(RbelHttpMessageFacet.class)
                && !element.hasFacet(RbelRootFacet.class);
          }

          @Override
          public ContainerTag performRendering(
              RbelElement element, Optional<String> key, RbelHtmlRenderingToolkit context) {
            return div(t1ms("Undecipherable EPA VAU message"))
                .with(
                    element
                        .getFacetOrFail(RbelUndecipherableVauEpaFacet.class)
                        .getErrorNotes()
                        .stream()
                        .map(note -> RbelHtmlRenderingToolkit.createNote("mb-5", note))
                        .collect(Collectors.toList()))
                .with(
                    text(
                        StringUtils.abbreviate(
                            Base64.getEncoder().encodeToString(element.getRawContent()), 100)));
          }
        });
  }

  private List<RbelNoteFacet> errorNotes;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }
}
