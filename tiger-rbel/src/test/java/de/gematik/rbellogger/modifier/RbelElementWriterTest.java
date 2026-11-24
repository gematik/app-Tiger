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
package de.gematik.rbellogger.modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.util.RbelContent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RbelElementWriterTest {

  @Test
  void writerImplementingOnlyTheByteArrayMethodStillWorks() {
    RbelElementWriter legacyWriter =
        new RbelElementWriter() {
          @Override
          public boolean canWrite(RbelElement oldTargetElement) {
            return true;
          }

          @Override
          public byte[] write(
              RbelElement oldTargetElement, RbelElement oldTargetModifiedChild, byte[] newContent) {
            return Arrays.copyOf(newContent, newContent.length + 1);
          }
        };

    var result = legacyWriter.write(null, null, RbelContent.wrap("abc".getBytes()));

    assertThat(result.toByteArray()).containsExactly('a', 'b', 'c', 0);
  }

  @Test
  void writerImplementingNeitherMethodFailsWithAClearMessage() {
    RbelElementWriter emptyWriter = oldTargetElement -> true;

    assertThatThrownBy(() -> emptyWriter.write(null, null, RbelContent.wrap("abc".getBytes())))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("must implement write(RbelElement, RbelElement, RbelContent) or");
  }
}
