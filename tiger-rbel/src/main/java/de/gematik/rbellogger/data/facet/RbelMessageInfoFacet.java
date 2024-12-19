/*
 *
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

import lombok.AllArgsConstructor;
import lombok.Data;

/** Facet which carries information about how to represent this message when rendering */
@Data
@AllArgsConstructor
public class RbelMessageInfoFacet implements RbelFacet {
  private final String symbol;
  private final String color;
  private final String menuInfoString;
  private final String abbrev;
  private final String title;

  public static RbelFacet newErrorSymbol(String menuInfoString) {
    return new RbelMessageInfoFacet("fa-triangle-exclamation", "text-danger", menuInfoString, "ERR", "Error");
  }
}
