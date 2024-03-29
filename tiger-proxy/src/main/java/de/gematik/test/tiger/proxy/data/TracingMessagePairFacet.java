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

package de.gematik.test.tiger.proxy.data;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.RbelFacet;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TracingMessagePairFacet implements RbelFacet {

  private final RbelElement response;
  private final RbelElement request;

  @Override
  public RbelMultiMap<RbelElement> getChildElements() {
    return new RbelMultiMap<>();
  }

  public Optional<RbelElement> getOtherMessage(RbelElement thisMessage) {
    if (thisMessage.equals(request)) {
      return Optional.of(response);
    } else if (thisMessage.equals(response)) {
      return Optional.of(request);
    } else {
      return Optional.empty();
    }
  }
}
