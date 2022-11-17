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

package de.gematik.rbellogger.modifier;

import de.gematik.rbellogger.converter.brainpool.BrainpoolCurves;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJwtSignature;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.key.RbelKeyManager;
import de.gematik.rbellogger.util.JsonUtils;
import lombok.AllArgsConstructor;
import org.jose4j.jca.ProviderContext;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.consumer.InvalidJwtSignatureException;
import org.jose4j.lang.JoseException;

import java.security.Key;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@AllArgsConstructor
public class RbelJwtWriter implements RbelElementWriter {

    static {
        BrainpoolCurves.init();
    }

    private final RbelKeyManager rbelKeyManager;

    @Override
    public boolean canWrite(RbelElement oldTargetElement) {
        return oldTargetElement.hasFacet(RbelJwtFacet.class);
    }

    @Override
    public byte[] write(RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
        final RbelJwtFacet jwtFacet = oldTargetElement.getFacetOrFail(RbelJwtFacet.class);

        return createUpdatedJws(oldTargetModifiedChild, new String(newContent, UTF_8), jwtFacet).getBytes(UTF_8);
    }

    private String createUpdatedJws(RbelElement oldTargetModifiedChild, String newContent, RbelJwtFacet jwtFacet) {
        final JsonWebSignature jws = new JsonWebSignature();

        ProviderContext context = new ProviderContext();
        context.getSuppliedKeyProviderContext().setGeneralProvider("BC");
        jws.setProviderContext(context);

        writeHeaderInJws(oldTargetModifiedChild, newContent, jwtFacet, jws);

        jws.setPayload(extractJwsBodyClaims(oldTargetModifiedChild, newContent, jwtFacet));

        if (jwtFacet.getSignature() == oldTargetModifiedChild
            && newContent.startsWith(new String(RbelJwtSignatureWriter.VERIFIED_USING_MARKER, UTF_8))) {
            jws.setKey(findNewSignerKey(newContent));
        } else {
            jws.setKey(extractJwsKey(jwtFacet));
        }

        if (!jwtFacet.getSignature().getFacetOrFail(RbelJwtSignature.class).isValid()) {
            throw new InvalidJwtSignatureException(
                "The signature is invalid\n" + jwtFacet.getSignature().printTreeStructure());
        }

        try {
            final String compactSerialization = jws.getCompactSerialization();

            if (jwtFacet.getSignature() == oldTargetModifiedChild
                && !newContent.startsWith(new String(RbelJwtSignatureWriter.VERIFIED_USING_MARKER, UTF_8))) {
                return compactSerialization.substring(0, compactSerialization.lastIndexOf('.')) + "." + newContent;
            }
            return compactSerialization;
        } catch (JoseException e) {
            throw new JwtUpdateException("Error writing into Jwt", e);
        }
    }

    private Key findNewSignerKey(String newContent) {
        final String newSignatureKeyName = newContent.substring(RbelJwtSignatureWriter.VERIFIED_USING_MARKER.length);
        final Key newSignatureKey = rbelKeyManager.findKeyByName(newSignatureKeyName)
            .map(key -> {
                if (key.getKey() instanceof PrivateKey) {
                    return key;
                } else {
                    return rbelKeyManager.findCorrespondingPrivateKey(newSignatureKeyName)
                        .orElseThrow(() -> new RbelJwtSignatureModificationException("Could not find private key matching '" + newSignatureKeyName + "'"));
                }
            })
            .map(RbelKey::getKey)
            .orElseThrow(() -> new RbelJwtSignatureModificationException("Could not find key '" + newSignatureKeyName + "'"));
        return newSignatureKey;
    }

    private Key extractJwsKey(RbelJwtFacet jwtFacet) {
        return jwtFacet.getSignature().getFacet(RbelJwtSignature.class)
            .map(RbelJwtSignature::getVerifiedUsing)
            .filter(Objects::nonNull)
            .flatMap(verifiedUsing -> verifiedUsing.seekValue(String.class))
            .flatMap(rbelKeyManager::findKeyByName)
            .flatMap(this::getKeyBasedOnEncryptionType)
            .orElseThrow(() -> new InvalidJwtSignatureException(
                "Could not find the key matching signature \n"
                    + jwtFacet.getSignature().printTreeStructureWithoutColors()
                    + "\n(If the private key is unknown then a new signature can not be written)"));
    }

    private void writeHeaderInJws(RbelElement oldTargetModifiedChild, String newContent, RbelJwtFacet jwtFacet,
                                  JsonWebSignature jws) {
        extractJwtHeaderClaims(oldTargetModifiedChild, newContent, jwtFacet)
            .forEach(pair -> jws.setHeader(pair.getKey(), pair.getValue()));
    }

    private List<Map.Entry<String, String>> extractJwtHeaderClaims(RbelElement oldTargetModifiedChild,
                                                                   String newContent,
                                                                   RbelJwtFacet jwtFacet) {
        if (jwtFacet.getHeader() == oldTargetModifiedChild) {
            return JsonUtils.convertJsonObjectStringToMap(newContent);
        } else {
            return JsonUtils.convertJsonObjectStringToMap(jwtFacet.getHeader().getRawStringContent());
        }
    }

    private String extractJwsBodyClaims(RbelElement oldTargetModifiedChild, String newContent,
                                        RbelJwtFacet jwtFacet) {
        if (jwtFacet.getBody() == oldTargetModifiedChild) {
            return newContent;
        } else {
            return jwtFacet.getBody().getRawStringContent();
        }
    }

    private Optional<Key> getKeyBasedOnEncryptionType(RbelKey rbelKey) {
        if (rbelKey.getKey().getAlgorithm().equals("HS256") || rbelKey.getKey().getAlgorithm().equals("HS512") || rbelKey.getKey().getAlgorithm().equals("HS384")) {
            return Optional.ofNullable(rbelKey.getKey());
        } else {
            return rbelKeyManager.findCorrespondingPrivateKey(rbelKey.getKeyName())
                .map(RbelKey::getKey);
        }
    }

    public class JwtUpdateException extends RuntimeException {

        public JwtUpdateException(String s, JoseException e) {
            super(s, e);
        }
    }

    public class InvalidJwtSignatureException extends RuntimeException {

        public InvalidJwtSignatureException(String s) {
            super(s);
        }
    }

    private class RbelJwtSignatureModificationException extends RuntimeException {
        public RbelJwtSignatureModificationException(String s) {
            super(s);
        }
    }
}
