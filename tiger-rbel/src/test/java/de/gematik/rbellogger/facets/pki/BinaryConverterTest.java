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
package de.gematik.rbellogger.facets.pki;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.data.RbelElement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BinaryConverterTest {

  /** How often the converter was asked to try, when none of the attempts succeeds. */
  private static int attemptsOn(String content) {
    var attempts = new AtomicInteger();
    BinaryConverter converter =
        (element, context, extractor) -> {
          attempts.incrementAndGet();
          return false;
        };

    BinaryConverter.consumeElement(
        converter, new RbelElement(content.getBytes(US_ASCII), null), null);

    return attempts.get();
  }

  @Test
  void contentThatCannotBeBase64IsOnlyTriedAsItIs() {
    assertThat(attemptsOn("hello world!")).isEqualTo(1);
    assertThat(attemptsOn("AAAAA")).isEqualTo(1);
    assertThat(attemptsOn("AA\nAAAA=")).isEqualTo(1);
  }

  @Test
  void contentThatCanBeBase64IsTriedWithTheOneDecoderThatCanDecodeIt() {
    assertThat(attemptsOn("AAAA")).isEqualTo(2);
    assertThat(attemptsOn("YQ==")).isEqualTo(2);
    assertThat(attemptsOn("ab+/")).isEqualTo(2);
    assertThat(attemptsOn("ab-_")).isEqualTo(2);
  }

  @Test
  void contentWithCharactersOfBothAlphabetsIsOnlyTriedAsItIs() {
    assertThat(attemptsOn("a+b-")).isEqualTo(1);
    assertThat(attemptsOn("a/b_")).isEqualTo(1);
  }

  @Test
  void aLineBreakAfterThePaddingIsNoReasonToSkipTheDecoder() {
    assertThat(attemptsOn("YQ==\n")).isEqualTo(2);
    assertThat(attemptsOn("YWI=\r\n")).isEqualTo(2);
  }
}
