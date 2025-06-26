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
package de.gematik.test.tiger.proxy.exceptions;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelMapFacet;
import de.gematik.rbellogger.data.core.RbelMessageInfoFacet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo()
@Slf4j
public class RbelTigerProxyExceptionConverter extends RbelConverterPlugin {

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (!rbelElement.hasFacet(TigerRoutingErrorFacet.class)) {
      return;
    }
    val routingException = rbelElement.getFacetOrFail(TigerRoutingErrorFacet.class).getException();
    val childElement = new RbelElement(new byte[] {}, rbelElement);
    rbelElement.getFacetOrFail(TigerRoutingErrorFacet.class).setErrorElement(childElement);
    val childNodes = new RbelMultiMap<RbelElement>();
    childElement.addFacet(new RbelMapFacet(childNodes));
    childNodes.put("message", RbelElement.wrap(childElement, routingException.getMessage()));
    childNodes.put("timestamp", RbelElement.wrap(childElement, routingException.getTimestamp()));
    if (routingException.getSenderAddress() != null) {
      childNodes.put("sender", RbelElement.wrap(childElement, routingException.getSenderAddress()));
    }
    if (routingException.getReceiverAddress() != null) {
      childNodes.put(
          "receiver", RbelElement.wrap(childElement, routingException.getReceiverAddress()));
    }

    final String errorMessage =
        Optional.ofNullable(routingException.getCause())
            .map(Throwable::getMessage)
            .orElseGet(routingException::getMessage);
    rbelElement.addFacet(RbelMessageInfoFacet.newErrorSymbol(errorMessage));
  }
}
