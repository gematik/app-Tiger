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

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.vau.vau_erp.RbelErpVauDecrpytionConverter;
import de.gematik.rbellogger.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.util.Base64;
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

    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder().rbelFile("src/test/resources/rezepsFiltered.tgr").build();
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .activateConversionFor("erp-vau")
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(fileReaderCapturer));
    log.info("cont init...");
    fileReaderCapturer.initialize();
    log.info("Initialized!");
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    assertThat(RbelHtmlRenderer.render(rbelLogger.getMessageHistory())).isNotBlank();
  }

  @Test
  void keyIdEndingInOneInRequest_shouldStillParseCorrectly() {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder().rbelFile("src/test/resources/tgr1810VauErp.tgr").build();
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .activateConversionFor("erp-vau")
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(fileReaderCapturer));
    fileReaderCapturer.initialize();

    RbelElementAssertion.assertThat(rbelLogger.getMessageList().get(12))
        .hasStringContentEqualToAtPosition(
            "$.body.message.body.Parameters.xmlns", "http://hl7.org/fhir");
  }

  @Test
  void testNestedRbelPathIntoErpRequest() {
    assertThat(
            rbelLogger
                .getMessageList()
                .get(52)
                .findRbelPathMembers(
                    "$.body.message.body.Parameters.parameter.valueCoding.system.value")
                .get(0)
                .getRawStringContent())
        .isEqualTo("https://gematik.de/fhir/CodeSystem/Flowtype");
  }

  @Test
  void fixedSecretKeyOnly() throws Exception {
    byte[] decodedKey = Base64.getDecoder().decode("krTNhsSUEfXvy6BZFp5G4g==");
    RbelLogger rbelLogger = RbelLogger.build();
    rbelLogger.getRbelConverter().addConverter(new RbelErpVauDecrpytionConverter());
    final RbelFileReaderCapturer fileReaderCapturer =
        new RbelFileReaderCapturer(
            rbelLogger.getRbelConverter(),
            "src/test/resources/rezeps_traffic_krTNhsSUEfXvy6BZFp5G4g==.tgr");
    rbelLogger
        .getRbelKeyManager()
        .addKey(
            "VAU Secret Key krTNhsSUEfXvy6BZFp5G4g",
            new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES"),
            0);
    fileReaderCapturer.initialize();
    fileReaderCapturer.close();

    assertThat(
            rbelLogger
                .getMessageList()
                .get(45)
                .findElement("$.body.keyId")
                .get()
                .seekValue(String.class))
        .contains("VAU Secret Key krTNhsSUEfXvy6BZFp5G4g");
  }

  @Test
  void testNestedRbelPathIntoErpVauResponse() {
    assertThat(
            rbelLogger
                .getMessageList()
                .get(54)
                .findRbelPathMembers("$.body.message.body.Task.identifier.system.value")
                .stream()
                .map(RbelElement::getRawStringContent)
                .toList())
        .containsExactly(
            "https://gematik.de/fhir/NamingSystem/PrescriptionID",
            "https://gematik.de/fhir/NamingSystem/AccessCode");
  }
}
