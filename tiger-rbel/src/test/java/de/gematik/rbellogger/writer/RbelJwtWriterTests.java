package de.gematik.rbellogger.writer;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.RbelConverter;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelJwtFacet;
import org.junit.jupiter.api.Test;

class RbelJwtWriterTests {

    private final RbelLogger logger = RbelLogger.build(new RbelConfiguration()
        .addInitializer(new RbelKeyFolderInitializer("src/test/resources")));
    private RbelConverter rbelConverter = logger.getRbelConverter();

    @Test
    void testSimpleJwtSerialization() {
        final RbelElement input = rbelConverter.convertElement("{\n"
            + "  \"tgrEncodeAs\":\"JWT\",\n"
            + "  \"header\":{\n"
            + "    \"alg\": \"BP256R1\",\n"
            + "    \"typ\": \"JWT\"\n"
            + "  },\n"
            + "  \"body\":{\n"
            + "    \"sub\": \"1234567890\",\n"
            + "    \"name\": \"John Doe\",\n"
            + "    \"iat\": 1516239022\n"
            + "  },\n"
            + "  \"signature\":{\n"
            + "    \"verifiedUsing\":\"idpEnc\"\n"
            + "  }\n"
            + "}", null);

        var output = serializeElement(input);

        System.out.println(output.printTreeStructure());

        assertThat(output)
            .hasFacet(RbelJwtFacet.class)
            .extractChildWithPath("$.signature.verifiedUsing")
            .hasValueEqualTo("puk_idpEnc");
    }

    private RbelElement serializeElement(RbelElement input) {
        return rbelConverter.convertElement(
            new RbelWriter(rbelConverter).serialize(input),
            null);
    }
}
