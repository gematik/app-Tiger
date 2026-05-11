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
package de.gematik.rbellogger.facets.ldap;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.ConverterInfo;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Pairs LDAP response messages with their corresponding request based on the LDAP messageId. In
 * LDAP, a single request can trigger multiple responses: SEARCH_REQUEST yields multiple
 * SEARCH_RESULT_ENTRY / SEARCH_RESULT_REFERENCE followed by SEARCH_RESULT_DONE, and extended
 * requests may yield INTERMEDIATE_RESPONSE messages before the final EXTENDED_RESPONSE. This
 * converter adds all responses to the same {@link TracingMessagePairFacet} so they can be navigated
 * cyclically in the WebUI.
 */
@ConverterInfo(onlyActivateFor = "ldap")
@Slf4j
public class RbelLdapPairingConverter extends RbelConverterPlugin {

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_PARSING;
  }

  @Override
  public int getPriority() {
    // Run after the RbelLdapConverter
    return -10;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.getParentNode() != null) {
      return;
    }
    if (!rbelElement.hasFacet(RbelLdapFacet.class)
        || !rbelElement.hasFacet(RbelResponseFacet.class)) {
      return;
    }

    if (rbelElement.hasFacet(TracingMessagePairFacet.class)) {
      fixUpExistingPairing(rbelElement);
      return;
    }

    val responseLdapFacet = rbelElement.getFacetOrFail(RbelLdapFacet.class);
    val responseMsgId = extractMessageId(responseLdapFacet);
    if (responseMsgId.isEmpty()) {
      return;
    }

    converter
        .getConverter()
        .getPreviousMessages(rbelElement, msg -> isLdapRequestWithId(msg, responseMsgId.get()))
        .findFirst()
        .ifPresent(request -> pairResponseWithRequest(rbelElement, request));
  }

  /**
   * When a response was already paired during .tgr file reading (by {@link
   * de.gematik.rbellogger.file.TgrFilePairingPlugin}), the request/response assignment may be wrong
   * because protocol-specific facets were not yet available. This method corrects the assignment
   * now that LDAP facets are present.
   */
  private void fixUpExistingPairing(RbelElement response) {
    val pair = response.getFacetOrFail(TracingMessagePairFacet.class);
    if (pair.getRequest() != null && pair.getRequest().hasFacet(RbelRequestFacet.class)) {
      if (isTerminalResponse(response)) {
        pair.markAsResponseComplete();
      }
    }
  }

  private void pairResponseWithRequest(RbelElement response, RbelElement request) {
    boolean isTerminal = isTerminalResponse(response);
    val existingPair = request.getFacet(TracingMessagePairFacet.class);
    if (existingPair.isPresent()) {
      TracingMessagePairFacet pair = existingPair.get();
      pair.addResponse(response);
      if (isTerminal) {
        pair.markAsResponseComplete();
      }
      response.addOrReplaceFacet(pair);
    } else {
      TracingMessagePairFacet pair = new TracingMessagePairFacet(response, request);
      boolean isMultiResponse = isMultiResponseRequest(request);
      if (isMultiResponse && !isTerminal) {
        pair.markAsResponseIncomplete();
      }
      request.addOrReplaceFacet(pair);
      response.addOrReplaceFacet(pair);
    }
  }

  private boolean isTerminalResponse(RbelElement element) {
    return extractOperationType(element).map(LdapOperationType::isTerminalResponse).orElse(false);
  }

  private boolean isMultiResponseRequest(RbelElement element) {
    return extractOperationType(element)
        .map(LdapOperationType::isMultiResponseRequest)
        .orElse(false);
  }

  private Optional<LdapOperationType> extractOperationType(RbelElement element) {
    return element
        .getFacet(RbelLdapFacet.class)
        .map(f -> f.getChildElements().get(RbelLdapFacet.PROTOCOL_OP_KEY))
        .flatMap(protocolOp -> protocolOp.getFacet(RbelLdapProtocolOpFacet.class))
        .map(RbelLdapProtocolOpFacet::getOperationType)
        .flatMap(RbelElement::printValue)
        .flatMap(LdapOperationType::fromName);
  }

  private boolean isLdapRequestWithId(RbelElement msg, int messageId) {
    return msg.hasFacet(RbelLdapFacet.class)
        && msg.hasFacet(RbelRequestFacet.class)
        && extractMessageId(msg.getFacetOrFail(RbelLdapFacet.class))
            .map(id -> id == messageId)
            .orElse(false);
  }

  private Optional<Integer> extractMessageId(RbelLdapFacet ldapFacet) {
    return ldapFacet
        .getChildElements()
        .get(RbelLdapFacet.MSG_ID_KEY)
        .printValue()
        .map(
            s -> {
              try {
                return Integer.parseInt(s);
              } catch (NumberFormatException e) {
                return null;
              }
            });
  }
}
