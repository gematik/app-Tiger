/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.test.tiger.proxy.vau;

import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RbelVauSessionListener implements RbelConverterPlugin {

    private static final String RECORD_ID_KVNR_RBEL_PATH =
        "$.Data.content.decoded.AuthorizationAssertion.content.decoded." +
            "Assertion.AttributeStatement.Attribute.AttributeValue.RecordIdentifier." +
            "InsurantId.extension";

    // For technical details please see gemSpec_Krypt chapter 3.15 and chapter 6

    private final Map<String, VauSessionFacet> clientHelloHashToSessionMap = new HashMap<>();
    private final Map<String, VauSessionFacet> keyIdToSessionMap = new HashMap<>();

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
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
        if (rbelElement.getFacet(RbelRootFacet.class)
            .map(RbelRootFacet::getRootFacet)
            .filter(RbelJsonFacet.class::isInstance)
            .isPresent()) {
            final RbelJsonFacet rbelJsonFacet = rbelElement.getFacet(RbelJsonFacet.class).get();
            if (!rbelJsonFacet.getJsonElement().getAsJsonObject()
                .get("MessageType").getAsString()
                .equals("VAUClientHello")) {
                return;
            }
            rbelElement.findElement(RECORD_ID_KVNR_RBEL_PATH)
                .map(RbelElement::getRawStringContent)
                .ifPresent(insurantId -> rbelElement.addFacet(
                    VauSessionFacet.builder()
                        .recordId(RbelElement.wrap(rbelElement, insurantId))
                        .build()
                ));
        }
    }

    private void tagVauServerHello(RbelElement rbelElement) {
        if (rbelElement.getParentNode().hasFacet(RbelHttpResponseFacet.class)
            && rbelElement.getKey().map("body"::equals).orElse(false)) {
            rbelElement.getParentNode().getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getRequest)
                .flatMap(req -> req.getFacet(RbelHttpMessageFacet.class)
                    .map(RbelHttpMessageFacet::getBody)
                    .flatMap(body -> body.getFacet(VauSessionFacet.class)))
                .ifPresent(vauSessionFacet -> {
                    rbelElement.addFacet(vauSessionFacet.toBuilder().build());

                    rbelElement.findElement("$.Data.content.decoded.VAUClientHelloDataHash")
                        .map(RbelElement::getRawStringContent)
                        .ifPresent(hash -> clientHelloHashToSessionMap.put(hash, vauSessionFacet));
                });
        }
    }

    private void tagVauClientSigFin(RbelElement rbelElement) {
        if (rbelElement.getFacet(RbelRootFacet.class)
            .map(RbelRootFacet::getRootFacet)
            .filter(RbelJsonFacet.class::isInstance)
            .isPresent()) {
            rbelElement.findElement("$.VAUClientHelloDataHash")
                .map(RbelElement::getRawStringContent)
                .map(clientHelloHashToSessionMap::get)
                .filter(Objects::nonNull)
                .ifPresent(vauSessionFacet -> {
                    rbelElement.addFacet(vauSessionFacet.toBuilder().build());

                    rbelElement.findElement("$.FinishedData.content.keyId")
                        .map(RbelElement::getRawStringContent)
                        .ifPresent(hash -> keyIdToSessionMap.put(hash, vauSessionFacet));
                });
        }
    }

    private void tagVauServerFin(RbelElement rbelElement) {
        if (rbelElement.getParentNode().hasFacet(RbelHttpResponseFacet.class)
            && rbelElement.getKey().map("body"::equals).orElse(false)) {
            rbelElement.getParentNode().getFacet(RbelHttpResponseFacet.class)
                .map(RbelHttpResponseFacet::getRequest)
                .flatMap(req -> req.getFacet(RbelHttpMessageFacet.class)
                    .map(RbelHttpMessageFacet::getBody)
                    .flatMap(body -> body.getFacet(VauSessionFacet.class)))
                .ifPresent(vauSessionFacet ->
                    rbelElement.addFacet(vauSessionFacet.toBuilder().build()));
        }
    }

    private void tagDocvMessageByKeyId(RbelElement rbelElement) {
        if (rbelElement.getFacet(RbelRootFacet.class)
            .map(RbelRootFacet::getRootFacet)
            .filter(RbelVauEpaFacet.class::isInstance)
            .isPresent()) {
            rbelElement.findElement("$.keyId")
                .map(RbelElement::getRawStringContent)
                .map(keyIdToSessionMap::get)
                .filter(Objects::nonNull)
                .ifPresent(vauSessionFacet ->
                    rbelElement.addFacet(vauSessionFacet.toBuilder().build()));
        }
    }

    private void safeExecute(Runnable function) {
        try {
            function.run();
        } catch (RuntimeException e) {
            log.trace("Swallowed exception during safe execution in {}: {}",
                getClass().getSimpleName(), e);
        }
    }
}
