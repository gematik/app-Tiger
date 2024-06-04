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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelPop3ResponseFacet;
import de.gematik.rbellogger.data.pop3.RbelPop3Command;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RbelPop3CommandConverterTest {

  @ParameterizedTest
  @EnumSource(value = RbelPop3Command.class)
  void shouldConvertPop3Command(RbelPop3Command command) {
    String arguments = "foobar foobar";
    String input = command.name() + " " + arguments + "\r\n";
    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Command")
        .hasValueEqualTo(command)
        .andTheInitialElement()
        .extractChildWithPath("$.pop3Arguments")
        .hasStringContentEqualTo(arguments);
  }

  @ParameterizedTest
  @EnumSource(value = RbelPop3Command.class)
  void shouldConvertPop3CommandWithoutArguments(RbelPop3Command command) {
    String input = command.name() + "\r\n";
    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.pop3Command")
        .hasValueEqualTo(command)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.pop3Arguments");
  }

  @ParameterizedTest
  @EnumSource(value = RbelPop3Command.class)
  void shouldRejectPop3CommandNotEndingWithCrLf(RbelPop3Command command) {
    RbelElement element = convertToRbelElement(command + " foobar foobar");
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = RbelPop3Command.class)
  void shouldRejectSimplePop3CommandNotEndingWithCrLf(RbelPop3Command command) {
    RbelElement element = convertToRbelElement(command.name());
    assertThat(element.hasFacet(RbelPop3ResponseFacet.class)).isFalse();
  }

  private static RbelElement convertToRbelElement(String input) {
    return RbelLogger.build().getRbelConverter().convertElement(input, null);
  }
}
