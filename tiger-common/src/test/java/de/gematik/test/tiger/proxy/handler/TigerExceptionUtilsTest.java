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
package de.gematik.test.tiger.proxy.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TigerExceptionUtilsTest {

  public static Stream<Arguments> causeWithTypeTestCases() {
    final SocketException flatCauseMessage = new SocketException("flat cause message");
    final SocketException innerException = new SocketException("root cause message");
    final RuntimeException outerException = new RuntimeException("blub", innerException);
    return Stream.of(
        arguments(new RuntimeException("blub"), IOException.class, Optional.empty()),
        arguments(flatCauseMessage, SocketException.class, Optional.of(flatCauseMessage)),
        arguments(outerException, SocketException.class, Optional.of(innerException)),
        arguments(outerException, RuntimeException.class, Optional.of(outerException)),
        arguments(outerException, FileNotFoundException.class, Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource("causeWithTypeTestCases")
  void testGetCauseWithType(
      Exception exception, Class<Exception> clazz, Optional<Exception> expected) {
    assertThat(TigerExceptionUtils.getCauseWithType(exception, clazz)).isEqualTo(expected);
  }

  public static Stream<Arguments> causeWithMessageTestCases() {
    final SocketException flatCauseMessage = new SocketException("flat cause message");
    final SocketException innerException = new SocketException("root cause message");
    final RuntimeException outerException = new RuntimeException("blub", innerException);
    return Stream.of(
        arguments(new RuntimeException("blub"), "blab", Optional.empty()),
        arguments(flatCauseMessage, "flat cause message", Optional.of(flatCauseMessage)),
        arguments(outerException, "root cause message", Optional.of(innerException)),
        arguments(outerException, "blub", Optional.of(outerException)),
        arguments(outerException, "blab", Optional.empty()));
  }

  @ParameterizedTest
  @MethodSource("causeWithMessageTestCases")
  void testGetCauseWithMessage(
      Exception exception, String predicateMatcherString, Optional<Exception> expected) {
    assertThat(
            TigerExceptionUtils.getCauseWithMessageMatching(
                exception, s -> s.equals(predicateMatcherString)))
        .isEqualTo(expected);
  }
}
