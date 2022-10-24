/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelAsn1Facet;
import de.gematik.rbellogger.data.facet.RbelAsn1TaggedValueFacet;
import de.gematik.rbellogger.data.facet.RbelUriFacet;
import de.gematik.rbellogger.data.facet.RbelValueFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class RbelAsn1ConverterTest {

    private static RbelLogger rbelLogger;

    @BeforeAll
    public static void initRbelLogger() {
        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
        );
    }

    public void parseRezepsCapture() {
        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
            .addCapturer(RbelFileReaderCapturer.builder()
                .rbelFile("src/test/resources/rezepsFiltered.tgr")
                .build())
        );
        rbelLogger.getRbelCapturer().initialize();
    }

    @SneakyThrows
    @Test
    public void shouldRenderCleanHtml() {
        parseRezepsCapture();
        FileUtils.writeStringToFile(new File("target/asn1TestRender.html"),
            RbelHtmlRenderer.render(rbelLogger.getMessageHistory()));
    }

    @Test
    public void checkXmlInPkcs7InXml() throws IOException {
        // check OID
        final RbelElement convertMessage = rbelLogger.getRbelConverter().convertElement(
            readCurlFromFileWithCorrectedLineBreaks("src/test/resources/xmlWithNestedPkcs7.curl"), null
        );
        assertThat(convertMessage
            .findRbelPathMembers("$..author.type.value")
            .stream().map(RbelElement::getRawStringContent).collect(Collectors.toList()))
            .contains("Practitioner");
    }

    @Test
    public void asn1EnumeratedShouldBeParsed() {
        final RbelElement convertMessage = rbelLogger.getRbelConverter().convertElement("MAMKAVU=", null);

        assertThat(convertMessage.findRbelPathMembers("$.0").get(0).seekValue(BigInteger.class))
            .get()
            .extracting(BigInteger::intValueExact)
            .isEqualTo(85);
    }

    @Test
    public void testVariousRbelPathInPcap() {
        parseRezepsCapture();
        // check OID
        final RbelElement rbelMessage = rbelLogger.getMessageHistory().get(58);
        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.2.0")
            .get(0).getFacet(RbelValueFacet.class)
            .get().getValue())
            .isEqualTo("1.2.840.10045.4.3.2");

        // check X509-Version (Tagged-sequence)
        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.0.content")
            .get(0).getFacet(RbelValueFacet.class)
            .get().getValue())
            .isEqualTo(BigInteger.valueOf(2));

        // check OCSP URL
        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.7.content.3.1.content.0.1.content.content")
            .get(0).hasFacet(RbelUriFacet.class))
            .isTrue();

        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.7.content.3.1.content.0")
            .get(0).getFacet(RbelAsn1Facet.class))
            .isPresent();

        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.7.content.3.1.content.0")
            .get(0).getFacet(RbelAsn1Facet.class))
            .isPresent();

        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.7.content.3.1.content.0.1")
            .get(0).getFacet(RbelAsn1TaggedValueFacet.class))
            .isPresent();

        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.7.content.3.1.content.0.1.tag")
            .get(0)
            .getFacet(RbelValueFacet.class).get().getValue())
            .isEqualTo(6);

        assertThat(rbelMessage
            .findRbelPathMembers("$.body.0.7.content.3.1.content.0.1.content.content")
            .get(0).getRawStringContent())
            .isEqualTo("http://ehca.gematik.de/ocsp/");

        // Parse y-coordinate of signature (Nested in BitString)
        assertThat(rbelMessage
            .findRbelPathMembers("$.body.2.content.1").get(0)
            .getFacet(RbelValueFacet.class).get().getValue())
            .isEqualTo(new BigInteger("9528585714247878020400211740123936754253798904841060501006300662224159406199"));
    }

}