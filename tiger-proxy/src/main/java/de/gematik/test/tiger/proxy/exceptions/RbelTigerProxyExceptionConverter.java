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

package de.gematik.test.tiger.proxy.exceptions;

import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.facet.*;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo()
@Slf4j
public class RbelTigerProxyExceptionConverter implements RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
    if (!rbelElement.hasFacet(TigerRoutingErrorFacet.class)) {
      return;
    }
    val routingException = rbelElement.getFacetOrFail(TigerRoutingErrorFacet.class).getException();
    val childElement = new RbelElement(new byte[] {}, rbelElement);
    rbelElement.getFacetOrFail(TigerRoutingErrorFacet.class).setErrorElement(childElement);
    val childNodes = new RbelMultiMap<RbelElement>();
    childElement.addFacet(new RbelMapFacet(childNodes));
    childNodes.put("message", RbelElement.wrap(rbelElement, routingException.getMessage()));
    childNodes.put("timestamp", RbelElement.wrap(rbelElement, routingException.getTimestamp()));
    if (routingException.getSenderAddress() != null) {
      childNodes.put("sender", RbelElement.wrap(rbelElement, routingException.getSenderAddress()));
    }
    if (routingException.getReceiverAddress() != null) {
      childNodes.put(
          "receiver", RbelElement.wrap(rbelElement, routingException.getReceiverAddress()));
    }

    final String errorMessage =
        Optional.ofNullable(routingException.getCause())
            .map(Throwable::getMessage)
            .orElseGet(routingException::getMessage);
    rbelElement.addFacet(RbelMessageInfoFacet.newErrorSymbol(errorMessage));
  }
}
