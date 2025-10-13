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

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import org.junit.jupiter.api.Test;

class RbelUriConverterTest {

  @Test
  void parseUriWithPortAndQuery() {
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement(
                "http://localhost:8080/foo/bar/path?arg1=par1a&arg1=par1b&arg2=par2", null);

    assertThat(convertedMessage)
        .hasStringContentEqualToAtPosition("$.scheme", "http")
        .hasStringContentEqualToAtPosition("$.path", "/foo/bar/path")
        .hasStringContentEqualToAtPosition("$.authority", "localhost:8080")
        .hasStringContentEqualToAtPosition("$.query", "arg1=par1a&arg1=par1b&arg2=par2")
        .hasStringContentEqualToAtPosition("$.host", "localhost")
        .hasStringContentEqualToAtPosition("$.port", "8080")
        .hasStringContentEqualToAtPosition("$.arg2.value", "par2")
        .hasStringContentEqualToAtPosition("$.arg1[0].value", "par1a")
        .hasStringContentEqualToAtPosition("$.arg1[1].value", "par1b");
  }

  @Test
  void parseUriWithUserInfo() {
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement("http://user:pass@example.com:8080/path", null);

    assertThat(convertedMessage)
        .hasStringContentEqualToAtPosition("$.authority", "user:pass@example.com:8080")
        .hasStringContentEqualToAtPosition("$.userInfo", "user:pass")
        .hasStringContentEqualToAtPosition("$.host", "example.com")
        .hasStringContentEqualToAtPosition("$.port", "8080")
        .hasStringContentEqualToAtPosition("$.path", "/path");
  }

  @Test
  void fileUriOnUnix() {
    final RbelElement convertedMessage =
        RbelLogger.build()
            .getRbelConverter()
            .convertElement("file:///home/user/docs/file.txt", null);

    assertThat(convertedMessage)
        .doesNotHaveChildWithPath("$.authority")
        .hasStringContentEqualToAtPosition("$.scheme", "file")
        .hasStringContentEqualToAtPosition("$.path", "/home/user/docs/file.txt");
  }

  @Test
  void queryWithoutPath() {
    final RbelElement convertedMessage =
        RbelLogger.build().getRbelConverter().convertElement("http://example.com?foo=bar", null);

    assertThat(convertedMessage)
        .hasStringContentEqualToAtPosition("$.basicPath", "http://example.com")
        .hasStringContentEqualToAtPosition("$.scheme", "http")
        .hasStringContentEqualToAtPosition("$.path", "")
        .hasStringContentEqualToAtPosition("$.authority", "example.com")
        .hasStringContentEqualToAtPosition("$.query", "foo=bar")
        .hasStringContentEqualToAtPosition("$.host", "example.com");
  }
}
