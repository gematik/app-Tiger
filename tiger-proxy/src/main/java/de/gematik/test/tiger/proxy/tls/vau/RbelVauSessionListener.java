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

package de.gematik.test.tiger.proxy.tls.vau;

import com.fasterxml.jackson.databind.JsonNode;
import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.converter.*;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelRootFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.RbelHttpMessageFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.facets.jackson.RbelJsonFacet;
import de.gematik.rbellogger.facets.vau.vau.RbelVauEpaFacet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

@ConverterInfo(onlyActivateFor = "epa-vau")
@Slf4j
public class RbelVauSessionListener extends RbelConverterPlugin {

  private static final String RECORD_ID_KVNR_RBEL_PATH =
      "$.Data.content.decoded.AuthorizationAssertion.content.decoded."
          + "Assertion.AttributeStatement.Attribute.AttributeValue.RecordIdentifier."
          + "InsurantId.extension";

  // For technical details please see gemSpec_Krypt chapter 3.15 and chapter 6

  private final Map<String, VauSessionFacet> clientHelloHashToSessionMap = new HashMap<>();
  private final Map<String, VauSessionFacet> keyIdToSessionMap = new HashMap<>();

  @Override
  public RbelConversionPhase getPhase() {
    return RbelConversionPhase.CONTENT_ENRICHMENT;
  }

  @Override
  public void consumeElement(RbelElement rbelElement, RbelConversionExecutor converter) {
    if (rbelElement.hasFacet(VauSessionFacet.class)) {
      return;
    }

    safeExecute(() -> tagVauClientHello(rbelElement));
    safeExecute(() -> tagVauServerHello(rbelElement));
    safeExecute(() -> tagVauClientSigFin(rbelElement));
    safeExecute(() -> tagVauServerFin(rbelElement));
    safeExecute(() -> tagDocvMessageByKeyId(rbelElement));
  }

  private void tagVauClientHello(RbelElement rbelElement) {
    findVauHandshakeMessageWithCommandAndHttpType(
            rbelElement, "VAUClientHello", RbelHttpRequestFacet.class)
        .ifPresent(
            jsonRoot ->
                jsonRoot
                    .findElement(RECORD_ID_KVNR_RBEL_PATH)
                    .map(RbelElement::getRawStringContent)
                    .ifPresent(
                        insurantId ->
                            jsonRoot.addFacet(
                                VauSessionFacet.builder()
                                    .recordId(RbelElement.wrap(jsonRoot, insurantId))
                                    .build())));
  }

  private void tagVauServerHello(RbelElement rbelElement) {
    findVauHandshakeMessageWithCommandAndHttpType(
            rbelElement, "VAUServerHello", RbelHttpResponseFacet.class)
        .ifPresent(
            jsonRoot ->
                rbelElement
                    .getFacet(TracingMessagePairFacet.class)
                    .map(TracingMessagePairFacet::getRequest)
                    .flatMap(
                        req ->
                            req.getFacet(RbelHttpMessageFacet.class)
                                .map(RbelHttpMessageFacet::getBody)
                                .flatMap(body -> body.getFacet(VauSessionFacet.class)))
                    .ifPresent(
                        vauSessionFacet -> {
                          VauSessionFacet.buildFromOtherInstanceForRoot(vauSessionFacet, jsonRoot);

                          jsonRoot
                              .findElement("$.Data.content.decoded.VAUClientHelloDataHash")
                              .map(RbelElement::getRawStringContent)
                              .ifPresent(
                                  hash -> clientHelloHashToSessionMap.put(hash, vauSessionFacet));
                        }));
  }

  private void tagVauClientSigFin(RbelElement rbelElement) {
    findVauHandshakeMessageWithCommandAndHttpType(
            rbelElement, "VAUClientSigFin", RbelHttpRequestFacet.class)
        .ifPresent(
            jsonRoot -> {
              jsonRoot
                  .findElement("$.VAUClientHelloDataHash")
                  .map(RbelElement::getRawStringContent)
                  .map(clientHelloHashToSessionMap::get)
                  .ifPresent(
                      vauSessionFacet -> {
                        VauSessionFacet.buildFromOtherInstanceForRoot(vauSessionFacet, jsonRoot);

                        jsonRoot
                            .findElement("$.FinishedData.content.keyId")
                            .map(RbelElement::getRawStringContent)
                            .ifPresent(hash -> keyIdToSessionMap.put(hash, vauSessionFacet));
                      });
            });
  }

  private void tagVauServerFin(RbelElement rbelElement) {
    findVauHandshakeMessageWithCommandAndHttpType(
            rbelElement, "VAUServerFin", RbelHttpResponseFacet.class)
        .ifPresent(
            jsonRoot ->
                jsonRoot
                    .getParentNode()
                    .getFacet(TracingMessagePairFacet.class)
                    .map(TracingMessagePairFacet::getRequest)
                    .flatMap(
                        req ->
                            req.getFacet(RbelHttpMessageFacet.class)
                                .map(RbelHttpMessageFacet::getBody)
                                .flatMap(body -> body.getFacet(VauSessionFacet.class)))
                    .ifPresent(
                        vauSessionFacet ->
                            VauSessionFacet.buildFromOtherInstanceForRoot(
                                vauSessionFacet, jsonRoot)));
  }

  private void tagDocvMessageByKeyId(RbelElement rbelElement) {

    Optional.ofNullable(rbelElement)
        .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
        .map(RbelHttpMessageFacet::getBody)
        .filter(
            el ->
                el.getFacet(RbelRootFacet.class)
                    .map(RbelRootFacet::getRootFacet)
                    .filter(RbelVauEpaFacet.class::isInstance)
                    .isPresent())
        .ifPresent(
            vauRoot ->
                vauRoot
                    .findElement("$.keyId")
                    .map(RbelElement::getRawStringContent)
                    .map(keyIdToSessionMap::get)
                    .ifPresent(
                        vauSessionFacet ->
                            VauSessionFacet.buildFromOtherInstanceForRoot(
                                vauSessionFacet, vauRoot)));
  }

  private void safeExecute(Runnable function) {
    try {
      function.run();
    } catch (RuntimeException e) {
      log.atTrace()
          .addArgument(RbelVauSessionListener.class::getSimpleName)
          .addArgument(e)
          .log("Swallowed exception during safe execution in {}: {}");
    }
  }

  private Optional<RbelElement> findVauHandshakeMessageWithCommandAndHttpType(
      RbelElement rbelElement, String command, Class<? extends RbelFacet> httpType) {
    val jsonRootElement =
        Optional.ofNullable(rbelElement)
            .filter(el -> el.hasFacet(httpType))
            .flatMap(el -> el.getFacet(RbelHttpMessageFacet.class))
            .map(RbelHttpMessageFacet::getBody)
            .filter(
                el ->
                    el.getFacet(RbelRootFacet.class)
                        .map(RbelRootFacet::getRootFacet)
                        .filter(RbelJsonFacet.class::isInstance)
                        .isPresent());
    if (!jsonRootElement
        .flatMap(el -> el.getFacet(RbelJsonFacet.class))
        .map(RbelJsonFacet::getJsonElement)
        .map(json -> json.get("MessageType"))
        .map(JsonNode::textValue)
        .orElse("")
        .equals(command)) {
      return Optional.empty();
    }
    return jsonRootElement;
  }
}
