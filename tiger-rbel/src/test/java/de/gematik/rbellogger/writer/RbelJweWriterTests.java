package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJweFacet;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import de.gematik.rbellogger.key.RbelKey;
import de.gematik.test.tiger.common.jexl.TigerJexlContext;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class RbelJweWriterTests {

    private final RbelLogger logger = RbelLogger.build(new RbelConfiguration()
        .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
    private RbelConverter rbelConverter = logger.getRbelConverter();

    @Test
    void testHybridEccEncryption() {
        final RbelElement input = rbelConverter.convertElement("{\n"
            + "  \"tgrEncodeAs\": \"JWE\",\n"
            + "  \"header\": {\n"
            + "    \"alg\": \"ECDH-ES\",\n"
            + "    \"enc\": \"A256GCM\",\n"
            + "    \"cty\": \"NJWT\"\n"
            + "  },\n"
            + "  \"body\": {\n"
            + "    \"some_claim\": \"foobar\",\n"
            + "    \"other_claim\": \"code\"\n"
            + "  },\n"
            + "  \"encryptionInfo\": {\n"
            + "    \"decryptedUsingKeyWithId\": \"puk_idpEnc\"\n"
            + "  }\n"
            + "}", null);

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
        rbelConverter.getRbelKeyManager().addKey(RbelKey.builder()
                .key(new SecretKeySpec(Base64.getDecoder().decode(keyContent), "AES"))
                .keyName(keyName)
                .build());
        final RbelElement input = rbelConverter.convertElement("{\n"
            + "  \"tgrEncodeAs\": \"JWE\",\n"
            + "  \"header\": {\n"
            + "    \"alg\": \"dir\",\n"
            + "    \"enc\": \"A256GCM\",\n"
            + "    \"cty\": \"NJWT\"\n"
            + "  },\n"
            + "  \"body\": {\n"
            + "    \"some_claim\": \"foobar\",\n"
            + "    \"other_claim\": \"code\"\n"
            + "  },\n"
            + "  \"encryptionInfo\": {\n"
            + "    \"decryptedUsingKey\": \"" + keyContent + "\"\n"
            + "  }\n"
            + "}", null);

        var output = serializeElement(input);

        assertThat(output)
            .hasFacet(RbelJweFacet.class)
            .extractChildWithPath("$.encryptionInfo.decryptedUsingKeyWithId")
            .hasValueEqualTo(keyName);
    }

    private RbelElement serializeElement(RbelElement input) {
        return rbelConverter.convertElement(
            new RbelWriter(rbelConverter).serialize(input, new TigerJexlContext()).getContent(),
            null);
    }
}
