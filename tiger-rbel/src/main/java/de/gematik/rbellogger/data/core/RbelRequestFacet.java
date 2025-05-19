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

import java.util.Objects;
import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Getter
public class RbelRequestFacet extends RbelMessageInfoFacet {

  /** Mark this element for the message validation as requiring a matching response */
  private boolean responseRequired;

  public RbelRequestFacet(String menuInfoString, Boolean responseRequired) {
    super("fa-share", "has-text-link", menuInfoString, "REQ", "Request");
    this.responseRequired = Objects.requireNonNullElse(responseRequired, false);
  }
}
