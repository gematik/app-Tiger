/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
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
    return RbelLogger.build(RbelConfiguration.builder().build().activateConversionFor("pop3"))
        .getRbelConverter()
        .convertElement(input, null);
  }
}
