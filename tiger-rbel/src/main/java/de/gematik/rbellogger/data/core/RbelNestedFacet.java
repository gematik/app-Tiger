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

package de.gematik.rbellogger.data.core;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class RbelNestedFacet implements RbelFacet {

  private final RbelElement nestedElement;
  private final String nestedElementName;

  public RbelNestedFacet(RbelElement nestedElement) {
    this.nestedElement = nestedElement;
    this.nestedElementName = "content";
  }

  @Builder
  public RbelNestedFacet(RbelElement nestedElement, String nestedElementName) {
    this.nestedElement = nestedElement;
    if (StringUtils.isNotEmpty(nestedElementName)) {
      this.nestedElementName = nestedElementName;
    } else {
      this.nestedElementName = "content";
    }
  }

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<RbelElement>().with(nestedElementName, nestedElement);
  }
}
