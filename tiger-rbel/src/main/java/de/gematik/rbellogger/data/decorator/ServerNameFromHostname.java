/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.data.decorator;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.facet.RbelHostnameFacet;
import java.util.Optional;
import java.util.function.Function;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class ServerNameFromHostname implements Function<RbelElement, Optional<String>> {

  private String checkIfLocalProxy(String realAddress) {
    if (realAddress.startsWith("127.0.0.1")) {
      return "local client";
    }
    return realAddress;
  }

  private Optional<String> extractHostname(RbelElement hostNameElement) {
    return hostNameElement
        .getFacet(RbelHostnameFacet.class)
        .map(RbelHostnameFacet::toRbelHostname)
        .map(RbelHostname::getHostname);
  }

  @Override
  public Optional<String> apply(RbelElement element) {
    return extractHostname(element).map(this::checkIfLocalProxy);
  }
}
