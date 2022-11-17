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

package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJwtSignature;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.data.facet.RbelRootFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

@Slf4j
public class RbelJwtConverter implements RbelConverterPlugin {

    static {
        BrainpoolCurves.init();
    }

    @Override
    public void consumeElement(RbelElement rbelElement, RbelConverter converter) {
        try {
            final JsonWebSignature jsonWebSignature = initializeJws(rbelElement);

            final RbelElement headerElement = converter.convertElement(
                jsonWebSignature.getHeaders().getFullHeaderAsJsonString().getBytes(StandardCharsets.UTF_8),
                rbelElement);
            final RbelElement bodyElement = converter
                .convertElement(jsonWebSignature.getUnverifiedPayloadBytes(), rbelElement);
            final RbelElement signatureElement = new RbelElement(
                Base64.getUrlDecoder().decode(jsonWebSignature.getEncodedSignature()),
                rbelElement);
            signatureElement.addFacet(converter.getRbelKeyManager().getAllKeys()
                .map(rbelKey -> verifySig(jsonWebSignature, rbelKey.getKey(), rbelKey.getKeyName(), signatureElement))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findAny()
                .or(() -> tryToGetKeyFromX5cHeaderClaim(jsonWebSignature)
                    .map(key -> verifySig(jsonWebSignature, key, "x5c-header certificate", signatureElement))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                )
                .orElseGet(() -> RbelJwtSignature.builder()
                    .isValid(new RbelElement(null, signatureElement).addFacet(new RbelValueFacet(false)))
                    .verifiedUsing(null)
                    .build()
                ));
            final RbelJwtFacet rbelJwtFacet = new RbelJwtFacet(headerElement, bodyElement, signatureElement);
            rbelElement.addFacet(rbelJwtFacet);
            rbelElement.addFacet(new RbelRootFacet<>(rbelJwtFacet));
        } catch (JoseException e) {
            return;
        }
    }

    @SneakyThrows
    private Optional<PublicKey> tryToGetKeyFromX5cHeaderClaim(JsonWebSignature jsonWebSignature) {
        return Optional.ofNullable(jsonWebSignature.getCertificateChainHeaderValue())
            .map(list -> list.get(0))
            .map(X509Certificate::getPublicKey);
    }

    private JsonWebSignature initializeJws(RbelElement rbel) throws JoseException {
        final JsonWebSignature jsonWebSignature = new JsonWebSignature();

        ProviderContext context = new ProviderContext();
        context.getSuppliedKeyProviderContext().setGeneralProvider("BC");
        jsonWebSignature.setProviderContext(context);

        jsonWebSignature.setCompactSerialization(rbel.getRawStringContent());
        return jsonWebSignature;
    }

    private Optional<RbelJwtSignature> verifySig(final JsonWebSignature jsonWebSignature, final Key key,
        final String keyId, final RbelElement signatureElement) {
        try {
            jsonWebSignature.setKey(key);
            tryToGetKeyFromX5cHeaderClaim(jsonWebSignature);
            if (jsonWebSignature.verifySignature()) {
                return Optional.of(
                    RbelJwtSignature.builder()
                        .isValid(new RbelElement(null, signatureElement)
                            .addFacet(new RbelValueFacet(true)))
                        .verifiedUsing(new RbelElement(null, signatureElement)
                            .addFacet(new RbelValueFacet(keyId)))
                        .build());
            } else {
                return Optional.empty();
            }
        } catch (final JoseException e) {
            return Optional.empty();
        }
    }
}
