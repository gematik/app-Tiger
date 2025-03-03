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

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.io.IOException;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class RbelX509ConverterTest {

  private RbelElement xmlMessage;
  private RbelElement rawX509Certificate;

  @SneakyThrows
  @BeforeEach
  public void setUp() throws IOException {
    final RbelConverter converter =
        RbelLogger.build(
                RbelConfiguration.builder().activateRbelParsingFor(List.of("X509", "asn1")).build())
            .getRbelConverter();
    xmlMessage =
        converter.convertElement(
            readCurlFromFileWithCorrectedLineBreaks(
                    "src/test/resources/sampleMessages/xmlMessage.curl")
                .getBytes(),
            null);

    rawX509Certificate =
        converter.convertElement(
            new TigerPkiIdentity("src/test/resources/idpEnc.p12").getCertificate().getEncoded(),
            null);
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    log.info("About to render html...");
    final String render = RbelHtmlRenderer.render(List.of(xmlMessage, rawX509Certificate));
    log.info("Done rendering html! (length is {}k)", render.length() / 1000);
    FileUtils.writeStringToFile(
        FileUtils.getFile("target", "rbelX509ConverterTest.html"), render, "UTF-8");
    assertThat(render).isNotBlank();
  }

  @SneakyThrows
  @Test
  void shouldBeAccessibleViaRbelPath() {
    final RbelElement certificateElement =
        xmlMessage.findElement("$..[?(@.subject=~'.*TEST-ONLY.*')]").get();

    Assertions.assertThat(certificateElement)
        .isEqualTo(
            xmlMessage
                .findElement(
                    "$.body.RegistryResponse.RegistryErrorList.RegistryError.jwtTag.text.header.x5c.0.content")
                .get());
  }

  @SneakyThrows
  @Test
  void shouldParseX500ContentAsWell() {
    assertThat(xmlMessage.findElement("$..subject.CN").get().getRawStringContent())
        .isEqualTo("IDP Sig 3");
  }

  @SneakyThrows
  @Test
  void accessExtensionViaHumanReadableName() {
    assertThat(xmlMessage)
        .andPrintTree()
        .extractChildWithPath("$..extensions.[?(@.oid.name == 'keyUsage')]")
        .hasGivenValueAtPosition("$.critical", Boolean.TRUE)
        .hasGivenValueAtPosition("$.value", Hex.decode("03020780"));
  }
}
