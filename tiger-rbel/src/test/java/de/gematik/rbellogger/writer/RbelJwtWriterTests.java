package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import org.junit.jupiter.api.Test;

class RbelJwtWriterTests {

  private final RbelLogger logger =
      RbelLogger.build(
          new RbelConfiguration()
              .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
  private RbelConverter rbelConverter = logger.getRbelConverter();

  @Test
  void testSimpleJwtSerialization() {
    final RbelElement input =
        rbelConverter.convertElement(
            """
            {
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
                "verifiedUsing":"puk_idpEnc"
              }
            }
            """,
            null);

    var output = serializeElement(input);

    assertThat(output)
        .hasFacet(RbelJwtFacet.class)
        .extractChildWithPath("$.signature.verifiedUsing")
        .hasValueEqualTo("puk_idpEnc");
  }

  @Test
  void roundTripJwtSerialization() {
    final RbelElement input =
        rbelConverter.convertElement(
            "eyJhbGciOiJCUDI1NlIxIiwidHlwIjoiSldUIn0"
                + ".eyJzdWIiOiAiMTIzNDU2Nzg5MCIsIm5hbWUiOiAiSm9obiBEb2UiLCJpYXQiOiAxNTE2MjM5MDIyfQ"
                + ".XqVOo3RGw8eBjpDJtw7NVTRW5Io5BTQ9MiW4QVxhy5943glx3TFvkMqCvEtiuQDxpJwsrXMmkmUemBaZr1qzFw",
            null);

    var output = serializeElement(input);

    assertThat(output)
        .hasFacet(RbelJwtFacet.class)
        .extractChildWithPath("$.signature.verifiedUsing")
        .hasValueEqualTo("puk_idpEnc");
  }

  private RbelElement serializeElement(RbelElement input) {
    return rbelConverter.convertElement(
        new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent(), null);
  }
}
