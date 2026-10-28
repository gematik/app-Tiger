/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package de.gematik.rbellogger.facets.apdu;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import lombok.Getter;
import lombok.Value;

/** The decoded interindustry class byte of a command APDU, see {@link CommandClass}. */
@Value
public class RbelApduClassFacet implements RbelFacet {

  RbelElement chaining;
  RbelElement secureMessaging;
  RbelElement logicalChannel;

  public static RbelApduClassFacet of(RbelElement claElement, CommandClass commandClass) {
    return new RbelApduClassFacet(
        RbelElement.wrap(claElement, commandClass.chaining()),
        RbelElement.wrap(claElement, commandClass.secureMessaging().name()),
        RbelElement.wrap(claElement, commandClass.logicalChannel()));
  }

  public String describe() {
    return "logical channel "
        + logicalChannel.printValue().orElse("")
        + ", secure messaging "
        + secureMessaging.printValue().orElse("")
        + (chaining.seekValue(Boolean.class).orElse(false) ? ", command chaining" : "");
  }

  @Getter(lazy = true)
  RbelMultiMap<RbelElement> childElements =
      new RbelMultiMap<RbelElement>()
          .with("chaining", chaining)
          .with("secureMessaging", secureMessaging)
          .with("logicalChannel", logicalChannel);
}
