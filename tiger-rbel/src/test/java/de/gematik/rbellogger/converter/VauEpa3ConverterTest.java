/*
 * Copyright (c) 2024 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.rbellogger.converter;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class VauEpa3ConverterTest {
  private static RbelLogger rbelLogger;

  @BeforeAll
  @SneakyThrows
  public static void setUp() {
    rbelLogger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .setActivateVauEpa3Parsing(true)
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/vau3traffic.tgr")
                        .build()));
    try (final var capturer = rbelLogger.getRbelCapturer()) {
      capturer.initialize();
    }
  }

  @SneakyThrows
  @Test
  void shouldRenderCleanHtml() {
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessageHistory());
    Files.write(new File("target/vau3.html").toPath(), html.getBytes());
    assertThat(html).isNotBlank();
  }

  @SneakyThrows
  @Test
  void testDecryption() {
    assertThat(rbelLogger.getMessageList().get(1))
        .hasChildWithPath("$.body.AEAD_ct.decrypted_content");
    assertThat(rbelLogger.getMessageList().get(2))
        .hasChildWithPath("$.body.AEAD_ct.decrypted_content");
    assertThat(rbelLogger.getMessageList().get(4))
        .extractChildWithPath("$.body.decrypted")
        .hasStringContentEqualTo("Hello World");
    assertThat(rbelLogger.getMessageList().get(5))
        .extractChildWithPath("$.body.decrypted")
        .hasStringContentEqualTo("Right back at ya!")
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.reqCtr")
        .hasValueEqualTo(1l)
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.version")
        .hasValueEqualTo((byte) 2)
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.req")
        .hasValueEqualTo((byte) 2)
        .andTheInitialElement()
        .extractChildWithPath("$.body.header.keyId")
        .hasValueEqualTo(
            new BigInteger("49117871460386101168058772883563639427765135898532450228055942387686676034354"));
  }

  @Test
  void nestedPathProblems() throws Exception {
    var logger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .setActivateVauEpa3Parsing(true)
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/nestedPathProblems.tgr")
                        .build()));
    try (final var capturer = logger.getRbelCapturer()) {
      capturer.initialize();
    }
    assertThat(logger.getMessageList().get(9)).extractChildWithPath("$.body.decrypted.path");
  }

  @Test
  void nestedTransferEncoding() throws Exception {
    var logger =
        RbelLogger.build(
            new RbelConfiguration()
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .setActivateVauEpa3Parsing(true)
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/vau3WithInnerGzip.tgr")
                        .build()));
    try (final var capturer = logger.getRbelCapturer()) {
      capturer.initialize();
    }
    assertThat(logger.getMessageList().get(1)).extractChildWithPath("$.body.decrypted.body.resourceType")
      .hasStringContentEqualTo("Bundle");
  }
}
