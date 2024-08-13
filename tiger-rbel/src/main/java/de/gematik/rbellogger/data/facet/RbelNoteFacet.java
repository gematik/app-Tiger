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

import static j2html.TagCreator.div;
import static j2html.TagCreator.i;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import j2html.tags.UnescapedText;
import j2html.tags.specialized.DivTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
public class RbelNoteFacet implements RbelFacet {
  private final String value;
  private final NoteStyling style;

  public RbelNoteFacet(String value) {
    this.value = value;
    this.style = NoteStyling.INFO;
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }

  public DivTag renderToHtml() {
    return div(i(new UnescapedText(getValue().replace("\n", "<br/>"))))
        .withClass(getStyle().toCssClass());
  }

  @RequiredArgsConstructor
  public enum NoteStyling {
    INFO("text-info"),
    WARN("has-text-warning"),
    ERROR("has-text-danger");

    private final String cssClass;

    public String toCssClass() {
      return cssClass;
    }
  }
}
