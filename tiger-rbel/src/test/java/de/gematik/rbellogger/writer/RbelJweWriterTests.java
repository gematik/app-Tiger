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
package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.jose.RbelJweFacet;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RbelJweWriterTests {

  private final RbelLogger logger =
      RbelLogger.build(
          new RbelConfiguration()
              .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
  private RbelConverter rbelConverter = logger.getRbelConverter();

  @Test
  void testHybridEccEncryption() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "tgrEncodeAs": "JWE",
              "header": {
                "alg": "ECDH-ES",
                "enc": "A256GCM"
              },
              "body": {
                "some_claim": "foobar",
                "other_claim": "code"
              },
              "encryptionInfo": {
                "decryptedUsingKeyWithId": "idpEnc"
              }
            }
            """,
            null);

    var output = serializeElement(input);

    assertThat(output)
        .hasFacet(RbelJweFacet.class)
        .extractChildWithPath("$.encryptionInfo.decryptedUsingKeyWithId")
        .hasValueEqualTo("prk_idpEnc");
  }

  @Test
  void testDirectEncryption() {
    final String keyName = "manuallyAddedKeyFoobar";
    final String keyContent = "YVI2Ym5wNDVNb0ZRTWFmU1Y1ZTZkRTg1bG9za2tscjg";
    rbelConverter
        .getRbelKeyManager()
        .addKey(
            RbelKey.builder()
                .key(new SecretKeySpec(Base64.getDecoder().decode(keyContent), "AES"))
                .keyName(keyName)
                .build());
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "tgrEncodeAs": "JWE",
              "header": {
                "alg": "dir",
                "enc": "A256GCM"
              },
              "body": {
                "some_claim": "foobar",
                "other_claim": "code"
              },
              "encryptionInfo": {
                "decryptedUsingKey": "YVI2Ym5wNDVNb0ZRTWFmU1Y1ZTZkRTg1bG9za2tscjg"
              }
            }
            """,
            null);

    var output = serializeElement(input);

    assertThat(output)
        .hasFacet(RbelJweFacet.class)
        .extractChildWithPath("$.encryptionInfo.decryptedUsingKeyWithId")
        .hasValueEqualTo(keyName);
  }

  private RbelElement serializeElement(RbelElement input) {
    return rbelConverter.convertElement(
        new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent(), null);
  }
}
