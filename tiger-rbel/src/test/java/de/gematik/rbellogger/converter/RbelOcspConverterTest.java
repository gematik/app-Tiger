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
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelOcspResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@Slf4j
class RbelOcspConverterTest {

  private RbelElement ocspResponse = null;

  @SneakyThrows
  @BeforeEach
  public void setUp(TestInfo testInfo) throws IOException {
    log.info("Setting up test '{}'", testInfo.getDisplayName());
    final RbelConverter converter =
        RbelLogger.build(
                RbelConfiguration.builder().activateRbelParsingFor(List.of("OCSP", "asn1")).build())
            .getRbelConverter();
    final byte[] raw = Files.readAllBytes(Paths.get("src/test/resources/ocspResponse.pem"));
    ocspResponse = converter.convertElement(raw, null);
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    log.info("Rendering HTML");
    final String render = RbelHtmlRenderer.render(List.of(ocspResponse));
    log.info("HTML rendered length: {}", render.length());
    FileUtils.writeStringToFile(FileUtils.getFile("target", "ocsp.html"), render, "UTF-8");
    assertThat(render).isNotBlank();
  }

  @SneakyThrows
  @Test
  void shouldBeAccessibleViaRbelPath() {
    RbelElementAssertion.assertThat(ocspResponse)
        .hasFacet(RbelOcspResponseFacet.class)
        .hasGivenValueAtPosition("$.responseStatus", 0)
        .hasGivenValueAtPosition("$.version", 1)
        .hasGivenValueAtPosition("$.signatureAlgorithm.name", "ecdsaWithSHA256")
        .hasGivenValueAtPosition(
            "$.responderId", "C=DE,O=gematik NOT-VALID,CN=ehca OCSP Signer 61 nist TEST-ONLY")
        .hasGivenValueAtPosition("$.responses.0.hashAlgorithm", "1.3.14.3.2.26")
        .hasGivenValueAtPosition(
            "$.responses.0.issuerNameHash", "17D9A8825E1C3CE9610C022986205CE488FA2EC5")
        .hasGivenValueAtPosition(
            "$.responses.0.issuerKeyHash", "9F35E030A97FCAF8669F900A42CDBB81659F49FE")
        .hasGivenValueAtPosition("$.responses.0.serialNumber", "80461E979CD1")
        .hasGivenValueAtPosition("$.responses.0.extensions.0.oid", "1.3.36.8.3.12")
        .extractChildWithPath("$.responses.0.extensions.0.value")
        .hasStringContentEqualTo("\u0018\u000F20230831080046Z")
        .andTheInitialElement()
        .hasGivenValueAtPosition("$.responses.0.extensions.1.oid", "1.3.36.8.3.13");
  }
}
