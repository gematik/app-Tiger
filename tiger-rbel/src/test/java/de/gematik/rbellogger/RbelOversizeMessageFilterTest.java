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

package de.gematik.rbellogger;

import static de.gematik.rbellogger.TestUtils.readCurlFromFileWithCorrectedLineBreaks;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RbelOversizeMessageFilterTest {

  private static RbelLogger rbelLogger;

  @BeforeAll
  static void initializeRbelLogger() throws IOException {
    final String oversizedRequest =
        readCurlFromFileWithCorrectedLineBreaks("src/test/resources/sampleMessages/getRequest.curl")
            + "{\"foo\":\""
            + RandomStringUtils.randomAlphabetic(50_000_000)
            + "\"}\r\n";
    rbelLogger = RbelLogger.build();
    rbelLogger
        .getRbelConverter()
        .parseMessage(oversizedRequest.getBytes(), null, null, Optional.empty());
  }

  @Test
  void oversizedMessageShouldNotBeParsed() {
    assertThat(rbelLogger.getMessageList().get(0).getFirst("body").get().getFacets()).isEmpty();
  }

  @Test
  void oversizedMessageShouldNotBeRendered() throws Exception {
    final String html = RbelHtmlRenderer.render(rbelLogger.getMessageList());

    FileUtils.writeStringToFile(new File("target/large.html"), html, StandardCharsets.UTF_8);

    assertThat(html).hasSizeLessThan(1024 * 1024);
  }
}
