package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
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
