/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.util.Base64;
import java.util.stream.Collectors;
import javax.crypto.spec.SecretKeySpec;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Slf4j
public class VauErpConverterTest {
    private static RbelLogger rbelLogger;

    @BeforeAll
    public static void setUp() {
        log.info("Initializing...");

        final RbelFileReaderCapturer fileReaderCapturer = RbelFileReaderCapturer.builder()
            .rbelFile("src/test/resources/rezepsFiltered.tgr")
            .build();
        rbelLogger = RbelLogger.build(new RbelConfiguration()
            .addAdditionalConverter(new RbelErpVauDecrpytionConverter())
            .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
            .addCapturer(fileReaderCapturer)
        );
        log.info("cont init...");
        fileReaderCapturer.initialize();
        log.info("Initialized!");
    }

    @SneakyThrows
    @Test
    void shouldRenderCleanHtml() {
        assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory()))
            .isNotBlank();
    }

    @Test
    void testNestedRbelPathIntoErpRequest() {
        assertThat(rbelLogger.getMessageList().get(52)
            .findRbelPathMembers("$.body.message.body.Parameters.parameter.valueCoding.system.value")
            .get(0).getRawStringContent())
            .isEqualTo("https://gematik.de/fhir/CodeSystem/Flowtype");
    }

    @Test
    void fixedSecretKeyOnly() throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode("krTNhsSUEfXvy6BZFp5G4g==");
        RbelLogger rbelLogger = RbelLogger.build();
        rbelLogger.getRbelConverter().addConverter(new RbelErpVauDecrpytionConverter());
        final RbelFileReaderCapturer fileReaderCapturer = new RbelFileReaderCapturer(rbelLogger.getRbelConverter(),
            "src/test/resources/rezeps_traffic_krTNhsSUEfXvy6BZFp5G4g==.tgr");
        rbelLogger.getRbelKeyManager().addKey("VAU Secret Key krTNhsSUEfXvy6BZFp5G4g",
            new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES"), 0);
        fileReaderCapturer.initialize();
        fileReaderCapturer.close();

        assertThat(rbelLogger.getMessageList().get(47)
            .findElement("$.body.keyId")
            .get().seekValue(String.class).get())
            .isEqualTo("VAU Secret Key krTNhsSUEfXvy6BZFp5G4g");
    }

    @Test
    void testNestedRbelPathIntoErpVauResponse() {
        assertThat(rbelLogger.getMessageList().get(54)
            .findRbelPathMembers("$.body.message.body.Task.identifier.system.value")
            .stream().map(RbelElement::getRawStringContent).collect(Collectors.toList()))
            .containsExactly("https://gematik.de/fhir/NamingSystem/PrescriptionID",
                "https://gematik.de/fhir/NamingSystem/AccessCode");
    }

    @Test
    void testNestedRbelPathIntoSignedErpVauMessage() {
//          assertThat(rbelLogger.getMessageList().get(95)
//            .findRbelPathMembers("$.body.message.body.Bundle.entry.resource.Binary.data.value.1.content.2.1.content")
//            .get(0).getFacet(RbelXmlElement.class))
//            .isPresent();
    }
}
