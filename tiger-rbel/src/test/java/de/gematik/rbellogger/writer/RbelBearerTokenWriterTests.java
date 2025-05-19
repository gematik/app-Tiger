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

package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import org.junit.jupiter.api.Test;

class RbelBearerTokenWriterTests {

  private final RbelLogger logger =
      RbelLogger.build(
          new RbelConfiguration()
              .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
  private RbelConverter rbelConverter = logger.getRbelConverter();

  @Test
  void testNestedJwtSerialization() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            Bearer {
              "tgrEncodeAs":"JWT",
              "header":{
                "alg": "BP256R1",
                "typ": "JWT"
              },
              "body":{
                "sub": "1234567890",
                "name": "John Doe",
                "iat": 1516239022
              },
              "signature":{
                "verifiedUsing":"idpEnc"
              }
            }
            """,
            null);

    var output = serializeElement(input);

    assertThat(output)
        .extractChildWithPath("$.BearerToken.signature.verifiedUsing")
        .hasValueEqualTo("puk_idpEnc");
  }

  @Test
  void testPureJsonSerialization() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
              "tgrEncodeAs": "BEARER_TOKEN",
              "BearerToken": {
                "tgrEncodeAs": "JWT",
                "header": {
                  "alg": "BP256R1",
                  "typ": "JWT"
                },
                "body": {
                  "sub": "1234567890",
                  "name": "John Doe",
                  "iat": 1516239022
                },
                "signature": {
                  "verifiedUsing": "idpEnc"
                }
              }
            }
            """,
            null);

    var output = serializeElement(input);

    assertThat(output)
        .extractChildWithPath("$.BearerToken.signature.verifiedUsing")
        .hasValueEqualTo("puk_idpEnc");
  }

  private RbelElement serializeElement(RbelElement input) {
    return rbelConverter.convertElement(
        new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent(), null);
  }
}
