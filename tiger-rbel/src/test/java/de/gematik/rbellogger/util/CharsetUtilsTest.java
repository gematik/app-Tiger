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
package de.gematik.rbellogger.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;

class CharsetUtilsTest {

  @Test
  void charsetsThatEncodeAsciiAsItsOwnBytesAreAsciiCompatible() {
    for (Charset charset : new Charset[] {UTF_8, ISO_8859_1, US_ASCII}) {
      assertThat(CharsetUtils.isAsciiCompatible(charset)).as(charset.name()).isTrue();
      assertThat("Az09-_".getBytes(charset)).isEqualTo("Az09-_".getBytes(US_ASCII));
    }
  }

  @Test
  void wideCharsetsAndNoCharsetAreNot() {
    for (Charset charset : new Charset[] {UTF_16, UTF_16BE, UTF_16LE}) {
      assertThat(CharsetUtils.isAsciiCompatible(charset)).as(charset.name()).isFalse();
    }
    assertThat(CharsetUtils.isAsciiCompatible(null)).isFalse();
  }
}
