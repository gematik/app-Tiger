/*
 *
 * Copyright 2025 gematik GmbH
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.converter.initializers.RbelKeyFolderInitializer;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.*;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.io.File;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

class RbelB64GzipConverterTest {

  private final RbelConfiguration config = new RbelConfiguration().activateConversionFor("b64gzip");
  private final RbelConverter rbelConverter = RbelLogger.build(config).getRbelConverter();

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertInPlace() {
    final RbelLogger rbelConverter =
        RbelLogger.build(
            new RbelConfiguration()
                .activateConversionFor("b64gzip")
                .addInitializer(new RbelKeyFolderInitializer("src/test/resources"))
                .addCapturer(
                    RbelFileReaderCapturer.builder()
                        .rbelFile("src/test/resources/nestedGzippedContent.tgr")
                        .build()));
    rbelConverter.getRbelCapturer().initialize();

    final RbelElement postFmvsdmResponse =
        rbelConverter.getMessageList().stream()
            .filter(e -> e.hasFacet(RbelHttpResponseFacet.class))
            .filter(
                request ->
                    request
                        .getFacet(RbelHttpResponseFacet.class)
                        .get()
                        .getRequest()
                        .getRawStringContent()
                        .contains("/service/fmvsdm"))
            .findFirst()
            .get();

    RbelElementAssertion.assertThat(postFmvsdmResponse)
        .extractChildWithPath("$.body.Envelope.Body.ReadVSDResponse.Pruefungsnachweis.text")
        .hasFacet(RbelB64GzipFacet.class)
        .extractChildWithPath("$.unzipped.PN")
        .hasFacet(RbelXmlFacet.class);
  }

  @SneakyThrows
  @Test
  void convertMessage_shouldConvertValidMessages() {
    final ObjectMapper jsonMapper =
        new ObjectMapper().configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
    final Map<String, String> test_cases =
        jsonMapper.readValue(
            FileUtils.readFileToByteArray(new File("src/test/resources/gzip/zippedMessages.json")),
            new TypeReference<>() {});

    for (Map.Entry<String, String> entry : test_cases.entrySet()) {
      final byte[] message = entry.getKey().getBytes();

      final RbelElement convertedElement = rbelConverter.convertElement(message, null);
      RbelElementAssertion.assertThat(convertedElement)
          .hasFacet(RbelB64GzipFacet.class)
          .extractChildWithPath("$.unzipped")
          .hasStringContentEqualTo(entry.getValue());
    }
  }

  @Test
  void convertMessage_shouldIgnoreInvalidMessage() {
    final byte[] invalidMessage = "H4sgarbage".getBytes();
    final RbelElement convertedElement = rbelConverter.convertElement(invalidMessage, null);

    assertThat(convertedElement.hasFacet(RbelLdapFacet.class)).isFalse();
    assertThat(convertedElement.findElement("$.unzipped")).isEmpty();
  }

  @Test
  void shouldRenderCleanHtml() {
    final byte[] message =
        "H4sIAAAAAAAA/x2N0W6CMBhGX4X0dhl/qSxblrZmTlxcBhpBA94YhOKK0JqVFd3Tr/HmuzjJdw6dXvvOs+LHSK0YCnyMPKEqXUt1YmibLR5fkGeGUtVlp5Vg6CYMmnK6Tjx3VIah72G4vAKMxj+Jvhzk2a8FNCVYU/dwUSPYu/R9Hh920SZdrpJ7xjFOs5QTTJ4wIWGAg+cwoOAQjTihELnInk+aW7ppK6v6OpvIxRcuYnhYrqP9LDe/TT+Li/w8D6+qykUb6aL72H6O+u+txXJnj+PIKDiJm4T/Ayj6/dLqAAAA"
            .getBytes();
    final RbelElement convertedElement = rbelConverter.convertElement(message, null);
    assertThat(convertedElement.hasFacet(RbelB64GzipFacet.class)).isTrue();
    final String html = RbelHtmlRenderer.render(List.of(convertedElement));

    assertThat(html).contains("PZ");
  }
}
