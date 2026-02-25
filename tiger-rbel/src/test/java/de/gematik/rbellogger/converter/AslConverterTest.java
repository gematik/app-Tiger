/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AslConverterTest {
  private static RbelLogger rbelLogger;

  @BeforeAll
  @SneakyThrows
  static void setUp() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .activateConversionFor("asl")
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/aslLog.tgr")
                        .build()));
    try (final var capturer = rbelLogger.getRbelCapturer()) {
      capturer.initialize();
    }
  }

  @SneakyThrows
  @Test
  void testDecryption() {
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessages());
    Files.write(new File("target/aslDecryption.html").toPath(), html.getBytes());
    assertThat(rbelLogger.getMessageList().get(4))
        .extractChildWithPath("$.body.decrypted.path")
        .hasStringContentEqualTo("/vsdservice/v1/vsdmbundle")
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.reqCtr")
        .hasValueEqualTo(1L)
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.version")
        .hasValueEqualTo((byte) 2)
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.req")
        .hasValueEqualTo((byte) 1)
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.keyId")
        .hasValueEqualTo(
            new BigInteger(
                "-13431957876526344696399548969088064807279215589977575230103306955275976302110"));
    assertThat(html)
        .contains(List.of("ASL Encrypted Message", "ASL Encrypted Message"))
        .doesNotContain("VAU 3", "VAU3");
  }
}
