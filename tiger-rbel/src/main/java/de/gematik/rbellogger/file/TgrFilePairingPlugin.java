/*
 *
 * Copyright 2026 gematik GmbH
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
package de.gematik.rbellogger.file;

import static de.gematik.rbellogger.data.RbelMessageMetadata.PAIRED_MESSAGE_UUID;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import lombok.val;

/**
 * Converter plugin that pairs request/response messages when reading from a .tgr file. Uses the
 * {@link RbelMessageMetadata#PAIRED_MESSAGE_UUID} metadata to find the partner message. Falls back
 * to HTTP-based heuristic pairing for legacy files without explicit pairing metadata.
 */
public class TgrFilePairingPlugin extends RbelConverterPlugin {

  private static final byte[] HTTP_PREFIX = "HTTP/".getBytes();

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.PREPARATION;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    val metadataFacet = rbelElement.getFacet(RbelMessageMetadata.class);
    if (metadataFacet.isEmpty() || !rbelElement.hasFacet(IncompleteMessageReadFromFile.class)) {
      return;
    }
    final var pairedUuid = PAIRED_MESSAGE_UUID.getValue(metadataFacet.get());
    if (pairedUuid.isPresent()) {
      pairWithExplicitPartner(rbelElement, pairedUuid.get(), converter);
    } else {
      pairPotentialHttpResponseWithPreviousMessage(rbelElement, converter);
    }
  }

  private void pairWithExplicitPartner(
      RbelElement rbelElement, String partnerUuid, RbelConversionExecutor converter) {
    converter.findMessageByUuid(partnerUuid).ifPresent(other -> applyPairing(rbelElement, other));
  }

  /**
   * Pairs current (the message being read now) with other (the partner found via pairedMessageUuid
   * lookup). We use {@link RbelRequestFacet} when available (e.g. HTTP, which is parsed during
   * PREPARATION). For protocols parsed later (e.g. LDAP, parsed during CONTENT_PARSING), we fall
   * back to sequence number ordering as a heuristic: the earlier message is treated as the request.
   * Protocol-specific pairing converters (e.g. RbelLdapPairingConverter) can fix up any wrong
   * assignments later. If either message already has a {@link TracingMessagePairFacet} (from an
   * earlier pairing round), we merge into it.
   */
  private void applyPairing(RbelElement current, RbelElement other) {
    RbelElement request;
    RbelElement response;

    if (other.hasFacet(RbelRequestFacet.class)) {
      request = other;
      response = current;
    } else if (current.hasFacet(RbelRequestFacet.class)) {
      request = current;
      response = other;
    } else {
      // Neither has RbelRequestFacet yet — use sequence number as best-effort heuristic.
      // Protocol-specific converters will correct this later if needed.
      long currentSeq = current.getSequenceNumber().orElse(Long.MAX_VALUE);
      long otherSeq = other.getSequenceNumber().orElse(Long.MAX_VALUE);
      request = otherSeq <= currentSeq ? other : current;
      response = (request == other) ? current : other;
    }

    TracingMessagePairFacet pair =
        request
            .getFacet(TracingMessagePairFacet.class)
            .or(() -> other.getFacet(TracingMessagePairFacet.class))
            .map(
                existing -> {
                  RbelElement newResponse = current.equals(existing.getRequest()) ? other : current;
                  existing.addResponse(newResponse);
                  return existing;
                })
            .orElseGet(() -> new TracingMessagePairFacet(response, request));

    current.addOrReplaceFacet(pair);
    other.addOrReplaceFacet(pair);
  }

  private void pairPotentialHttpResponseWithPreviousMessage(
      RbelElement message, RbelConversionExecutor converter) {
    if (message.getContent().startsWith(HTTP_PREFIX)) {
      converter
          .getConverter()
          .getPreviousMessages(
              message,
              msg ->
                  msg.hasFacet(RbelHttpRequestFacet.class)
                      && !msg.hasFacet(TracingMessagePairFacet.class))
          .findFirst()
          .ifPresent(
              unpairedRequest -> {
                message.addOrReplaceFacet(new TracingMessagePairFacet(message, unpairedRequest));
                unpairedRequest.addOrReplaceFacet(
                    new TracingMessagePairFacet(message, unpairedRequest));
              });
    }
  }
}
