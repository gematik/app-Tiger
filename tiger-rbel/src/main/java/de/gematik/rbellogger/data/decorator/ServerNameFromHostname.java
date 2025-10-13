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
package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelHostnameFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.RbelTcpIpMessageFacet;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.util.Optional;
import java.util.function.Function;
import lombok.NoArgsConstructor;
import lombok.val;

@NoArgsConstructor
public class ServerNameFromHostname implements Function<RbelElement, Optional<String>> {

  @Override
  public Optional<String> apply(RbelElement hostNameElement) {
    val hostnameFacet = hostNameElement.getFacet(RbelHostnameFacet.class);
    if (hostnameFacet.isEmpty()) {
      return Optional.empty();
    }
    final RbelSocketAddress socketAddress = hostnameFacet.get().toRbelSocketAddress();
    final RbelElement msgElement = hostNameElement.findRootElement();
    if (socketAddress.isLoopbackAddress()) {
      if ((msgElement.hasFacet(RbelRequestFacet.class)
              && msgElement
                  .getFacet(RbelTcpIpMessageFacet.class)
                  .map(RbelTcpIpMessageFacet::getSender)
                  .filter(el -> el == hostNameElement)
                  .isPresent())
          || (msgElement.hasFacet(RbelResponseFacet.class)
              && msgElement
                  .getFacet(RbelTcpIpMessageFacet.class)
                  .map(RbelTcpIpMessageFacet::getReceiver)
                  .filter(el -> el == hostNameElement)
                  .isPresent())) return Optional.of("local client");
    }
    return Optional.empty();
  }
}
