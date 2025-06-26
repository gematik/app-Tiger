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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.pop3.RbelPop3Command;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseFacet;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.val;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RbelPop3CommandConverterTest {

  private static String randomizeCase(String input) {
    Random random = new Random();
    return input
        .chars()
        .mapToObj(
            ch -> random.nextBoolean() ? Character.toLowerCase(ch) : Character.toUpperCase(ch))
        .map(ch -> String.valueOf((char) ch.intValue()))
        .collect(Collectors.joining());
  }

  static Collection<Arguments> providePop3Commands() {
    val enumValuesAsString = Arrays.stream(RbelPop3Command.values()).map(Enum::toString).toList();
    val enumValuesLowerCase = enumValuesAsString.stream().map(String::toLowerCase).toList();
    val enumValuesMixedCase =
        enumValuesAsString.stream().map(RbelPop3CommandConverterTest::randomizeCase).toList();

    val result = new ArrayList<>(enumValuesAsString);
    result.addAll(enumValuesLowerCase);
    result.addAll(enumValuesMixedCase);

    return result.stream().map(Arguments::of).collect(Collectors.toList());
  }

  @ParameterizedTest
  @MethodSource("providePop3Commands")
  void shouldConvertPop3Command(String commandAsString) {
    String arguments = "foobar foobar";
    String input = commandAsString + " " + arguments + "\r\n";
    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Command")
        .hasValueEqualTo(RbelPop3Command.fromStringIgnoringCase(commandAsString))
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Arguments")
        .hasStringContentEqualTo(arguments);
  }

  @ParameterizedTest
  @MethodSource("providePop3Commands")
  void shouldConvertPop3CommandWithoutArguments(String commandAsString) {
    String input = commandAsString + "\r\n";
    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Command")
        .hasValueEqualTo(RbelPop3Command.fromStringIgnoringCase(commandAsString))
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Arguments");
  }

  @ParameterizedTest
  @MethodSource("providePop3Commands")
  void shouldRejectPop3CommandNotEndingWithCrLf(String commandAsString) {
    RbelElement element = convertToRbelElement(commandAsString + " foobar foobar");
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("providePop3Commands")
  void shouldRejectSimplePop3CommandNotEndingWithCrLf(String commandAsString) {
    RbelElement element = convertToRbelElement(commandAsString);
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  private static RbelElement convertToRbelElement(String input) {
    return RbelLogger.build(RbelConfiguration.builder().build().activateConversionFor("pop3"))
        .getRbelConverter()
        .convertElement(input, null);
  }
}
