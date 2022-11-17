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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.elements.RbelJwtSignature;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.rbellogger.modifier.RbelJwtWriter.InvalidJwtSignatureException;
import de.gematik.rbellogger.modifier.RbelJwtWriter.JwtUpdateException;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class JwtModifierTest extends AbstractModifierTest {

    @Test
    void modifyJwtHeader_shouldContainModifiedContent() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.header.kid")
            .replaceWith("not the real header")
            .build());

        final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

        assertThat(modifiedMessage.findElement("$.body").get()
            .hasFacet(RbelJwtFacet.class))
            .isTrue();
        assertThat(modifiedMessage.findElement("$.body.header.kid")
            .map(RbelElement::getRawStringContent).get())
            .isEqualTo("not the real header");
    }

    @Test
    void modifyJwtBody_shouldContainModifiedContent() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.body.authorization_endpoint")
            .replaceWith("not the auth endpoint")
            .build());
        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.body.token_endpoint")
            .replaceWith("not the token endpoint")
            .build());

        final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

        assertThat(modifiedMessage.findElement("$.body.body.authorization_endpoint")
            .map(RbelElement::getRawStringContent).get())
            .isEqualTo("not the auth endpoint");
        assertThat(modifiedMessage.findElement("$.body.body.token_endpoint")
            .map(RbelElement::getRawStringContent).get())
            .isEqualTo("not the token endpoint");
    }

    @Test
    void modifyJwtBody_jwtSignatureVerified() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.body.sso_endpoint")
            .replaceWith("not the original sso endpoint")
            .build());

        final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);
        final RbelJwtSignature signature = modifiedMessage.findElement("$.body.signature")
            .get().getFacetOrFail(RbelJwtSignature.class);

        assertThat(signature.isValid()).isTrue();
        assertThat(signature.getVerifiedUsing().seekValue(String.class)
            .get()).isEqualTo("puk_idp-fd-sig-refimpl-3");
    }

    @Test
    void modifyJwtHeader_jwtSignatureVerified() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.header.kid")
            .replaceWith("not the original kid")
            .build());

        final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);
        final RbelJwtSignature signature = modifiedMessage.findElement("$.body.signature")
            .get().getFacetOrFail(RbelJwtSignature.class);

        assertThat(signature.isValid()).isTrue();
        assertThat(signature.getVerifiedUsing().seekValue(String.class)
            .get()).isEqualTo("puk_idp-fd-sig-refimpl-3");
    }

    @Test
    void modifyJwtHeader_cantEditAlg() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");
        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.header.alg")
            .replaceWith("ES256")
            .build());

        assertThatThrownBy(() -> modifyMessageAndParseResponse(message))
            .isInstanceOf(JwtUpdateException.class)
            .hasMessageContaining("Error writing into Jwt")
            .hasRootCauseMessage("ES256/SHA256withECDSA expects a key using P-256 but was BP-256");
    }

    @Test
    void jwtSignature_changeVerifiedUsingToPuk() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.signature.verifiedUsing")
            .replaceWith("puk_idpEnc")
            .build());

        assertThat(message
            .findElement("$..verifiedUsing").get())
            .extracting(el -> el.seekValue(String.class).get())
            .isNotNull()
            .isNotEqualTo("puk_idpEnc");
        assertThat(modifyMessageAndParseResponse(message)
            .findElement("$..verifiedUsing"))
            .get()
            .extracting(el -> el.seekValue(String.class).get())
            .isEqualTo("puk_idpEnc");
    }

    @Test
    void jwtSignature_changeVerifiedUsingToPrk() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.signature.verifiedUsing")
            .replaceWith("prk_idpEnc")
            .build());

        assertThat(modifyMessageAndParseResponse(message)
            .findElement("$..verifiedUsing").get())
            .extracting(el -> el.seekValue(String.class).get())
            .isEqualTo("puk_idpEnc");
    }

    @Test
    void jwtSignature_changeVerifiedUsingToUnkownKey() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.signature.verifiedUsing")
            .replaceWith("unknown key")
            .build());

        assertThatThrownBy(() -> modifyMessageAndParseResponse(message))
            .hasMessageContaining("Could not find key 'unknown key'");
    }

    @Test
    void jwtSignature_changeSignatureItself() throws IOException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.signature")
            .replaceWith(Base64.getUrlEncoder().encodeToString("hullabulla, but not a signature".getBytes(StandardCharsets.UTF_8)))
            .build());

        assertThat(modifyMessageAndParseResponse(message)
            .findElement("$.body.signature").get())
            .extracting(RbelElement::getRawStringContent)
            .isEqualTo("hullabulla, but not a signature");
    }

    @Test
    void modifyJwt_noMatchingPrivateKeyFound() throws IOException, IllegalAccessException {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");
        var rbelLoggerWithoutKeys = RbelLogger.build();

        rbelLoggerWithoutKeys.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.header.kid")
            .replaceWith("not the original kid")
            .build());

        assertThatThrownBy(() -> rbelLoggerWithoutKeys.getRbelModifier().applyModifications(message))
            .isInstanceOf(InvalidJwtSignatureException.class)
            .hasMessageContaining("Could not find the key matching signature");
    }

    @Test
    void modifyJwt_noPublicAndPrivateKeysFound() throws Exception {
        final RbelElement message = readAndConvertCurlMessage("src/test/resources/sampleMessages/jwtMessage.curl");
        var rbelLoggerWithoutKeys = RbelLogger.build();

        rbelLoggerWithoutKeys.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.header.kid")
            .replaceWith("not the original kid")
            .build());

        assertThatThrownBy(() -> rbelLoggerWithoutKeys.getRbelModifier().applyModifications(message))
            .isInstanceOf(InvalidJwtSignatureException.class)
            .hasMessageContaining("Could not find the key matching signature");
    }

    @Test
    void modifyJwt_falseSecret() throws IOException {
        final RbelElement message = readAndConvertCurlMessage(
            "src/test/resources/sampleMessages/jwtMessageWithFalseSecret.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.body.name")
            .replaceWith("other name")
            .build());

        assertThatThrownBy(() -> modifyMessageAndParseResponse(message))
            .isInstanceOf(InvalidJwtSignatureException.class)
            .hasMessageContaining("Could not find the key matching signature");
    }

    @Test
    void modifyJwt_correctSecret() throws IOException {
        RbelKey secretKey = RbelKey.builder()
            .keyName("secretKey")
            .key(new SecretKeySpec(("n2r5u8x/A?D(G-KaPdSgVkYp3s6v9y$B").getBytes(StandardCharsets.UTF_8), AlgorithmIdentifiers.HMAC_SHA256))
            .build();
        rbelLogger.getRbelKeyManager().addKey(secretKey);

        final RbelElement message = readAndConvertCurlMessage(
            "src/test/resources/sampleMessages/jwtMessageWithSecret.curl");

        rbelLogger.getRbelModifier().addModification(RbelModificationDescription.builder()
            .targetElement("$.body.body.name")
            .replaceWith("other name")
            .build());

        final RbelElement modifiedMessage = modifyMessageAndParseResponse(message);

        final RbelJwtSignature signature = modifiedMessage.findElement("$.body.signature")
            .get().getFacetOrFail(RbelJwtSignature.class);

        assertThat(signature.isValid()).isTrue();
    }
}
