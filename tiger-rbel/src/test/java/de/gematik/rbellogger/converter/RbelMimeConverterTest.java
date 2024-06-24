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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.nio.file.Files;
import java.nio.file.Paths;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class RbelMimeConverterTest {

  @SneakyThrows
  private static byte[] readMimeMessage() {
    return Files.readAllBytes(Paths.get("src/test/resources/sampleMessages/sampleMail.txt"));
  }

  @Test
  void shouldConvertMimeMessage() {
    byte[] mimeMessage = readMimeMessage();
    String pop3Response = "+OK message follows\r\n"+new String(mimeMessage)+"\r\n.\r\n";
    var element = convertToRbelElement(pop3Response.getBytes());
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.body")
        .hasChildWithPath("$.header")
        .hasChildWithPath("$.body")
        .hasChildWithPath("$.body.preamble")
        .hasChildWithPath("$.body.parts")
        .hasChildWithPath("$.body.parts.0.header")
        .hasChildWithPath("$.body.parts.0.body")
        .hasChildWithPath("$.body.parts.1.header")
        .hasChildWithPath("$.body.parts.1.body")
        .doesNotHaveChildWithPath("$.body.epilogue");
  }

  private static RbelElement convertToRbelElement(byte[] input) {
    RbelConfiguration configuration =
        RbelConfiguration.builder().skipParsingWhenMessageLargerThanKb(-1).build();
    return RbelLogger.build(configuration).getRbelConverter().convertElement(input, null);
  }
}
