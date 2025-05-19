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

package de.gematik.rbellogger.converter;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelHostname;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.facets.smtp.RbelSmtpCommand;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.testutil.RbelElementAssertion;
import de.gematik.rbellogger.util.EmailConversionUtils;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RbelSmtpCommandConverterTest {

  private RbelConverter converter;

  @BeforeEach
  void init() {
    if (converter == null) {
      converter =
          RbelLogger.build(
                  new RbelConfiguration()
                      .activateConversionFor("smtp")
                      .activateConversionFor("mime"))
              .getRbelConverter();
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "EHLO x.y",
        "ehlo x.y",
        "HELO y.x",
        "helo y.x",
        "MAIL FROM:<@a,@b:user@d>",
        "mail FROM:<@a,@b:user@d>",
        "RCPT TO:<user@d>",
        "rcpt TO:<user@d>",
        "VRFY <@a,@b:user@d>",
        "vrfy <@a,@b:user@d>",
        "EXPN <@a,@b:user@d>",
        "expn <@a,@b:user@d>",
        "HELP HELP",
        "help HELP",
        "NOOP no op",
        "noop no op"
      })
  void shouldConvertSingleLineSmtpCommand(String commandLine) {
    String[] parts = commandLine.split(" ", 2);
    String command = parts[0];
    String arguments = parts[1];
    String input = commandLine + "\r\n";
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.smtpCommand")
        .hasValueEqualTo(RbelSmtpCommand.fromStringIgnoringCase(command))
        .andTheInitialElement()
        .extractChildWithPath("$.smtpArguments")
        .hasStringContentEqualTo(arguments)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.smtpBody");
  }

  @ParameterizedTest
  @ValueSource(strings = {"RSET", "NOOP", "QUIT", "HELP"})
  void shouldConvertCommandWithoutArguments(String command) {
    String input = command + "\r\n";
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.smtpCommand")
        .hasValueEqualTo(RbelSmtpCommand.fromStringIgnoringCase(command))
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.smtpArguments")
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.smtpBody");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "RSET",
        "rset",
        "reset\r\nxyz\r\n.\r\n",
        "DATA\r\n",
        "data\r\n",
        "AUTH PLAIN dGVzdAB0ZXN0ADEyMzQ=",
        "auth plain dGVzdAB0ZXN0ADEyMzQ=",
      })
  void shouldRejectMalformedCommand(String input) {
    RbelElement element = convertToRbelElement(input);

    RbelElementAssertion.assertThat(element).doesNotHaveChildWithPath("$.smtpCommand");
  }

  @Test
  void shouldConvertDataCommand() {
    String command = "DATA";
    String body =
        """
        .blablabla
        .bubl\r
        foobar\r
        .\r
        blabla
        """;
    String input =
        command + "\r\n" + EmailConversionUtils.duplicateDotsAtLineBegins(body) + "\r\n.\r\n";

    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.smtpCommand")
        .hasValueEqualTo(RbelSmtpCommand.fromStringIgnoringCase(command))
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.smtpArguments")
        .andTheInitialElement()
        .extractChildWithPath("$.smtpBody")
        .hasStringContentEqualTo(body);
  }

  @Test
  void shouldConvertAuthCommand() {
    String command = "AUTH";
    String arguments = "SASL";
    String body = "dGVzdAB0ZXN0ADEyMzQ=\r\ndGVzdAB0ZXN0ADEyMzQ=";
    String input = command + " " + arguments + "\r\n" + body + "\r\n";

    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.smtpCommand")
        .hasValueEqualTo(RbelSmtpCommand.fromStringIgnoringCase(command))
        .andTheInitialElement()
        .extractChildWithPath("$.smtpArguments")
        .hasStringContentEqualTo(arguments)
        .andTheInitialElement()
        .extractChildWithPath("$.smtpBody")
        .hasStringContentEqualTo(body);
  }

  @Test
  void shouldConvertAuthPlainCommand() {
    String command = "AUTH";
    String arguments = "PLAIN dGVzdAB0ZXN0ADEyMzQdGVzdAB0ZXN0ADEyMzQ=";
    String input = command + " " + arguments + "\r\n";

    RbelElement element = convertToRbelElement(input);
    RbelElementAssertion.assertThat(element)
        .extractChildWithPath("$.smtpCommand")
        .hasValueEqualTo(RbelSmtpCommand.fromStringIgnoringCase(command))
        .andTheInitialElement()
        .extractChildWithPath("$.smtpArguments")
        .hasStringContentEqualTo(arguments)
        .andTheInitialElement()
        .doesNotHaveChildWithPath("$.smtpBody");
  }

  @ParameterizedTest
  @ValueSource(strings = {"MAIL FROM:<x.y>", "RCPT TO:<y.z>"})
  void shouldRenderSmtpMessages(String command) throws IOException {
    String smtpMessage = command + "\r\n";
    byte[] smtpMessageBytes = smtpMessage.getBytes(StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        converter.parseMessage(
            smtpMessageBytes,
            new RbelMessageMetadata()
                .withSender(new RbelHostname("sender", 13421))
                .withReceiver(new RbelHostname("receiver", 14512))
                .withTransmissionTime(ZonedDateTime.now()));

    final String convertedHtml = RbelHtmlRenderer.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    String[] commandline = command.split(" ");
    assertThat(convertedHtml)
        .contains("SMTP Request")
        .contains("Command: </b>" + commandline[0])
        .contains("Arguments: </b>");
  }

  @SneakyThrows
  private static byte[] readMimeMessage(String path) {
    return Files.readAllBytes(Paths.get("src/test/resources/" + path));
  }

  @Test
  void shouldRenderSmtpDataMessages() throws IOException {
    String mimeMessage =
        new String(readMimeMessage("sampleMessages/sampleMail.txt"), StandardCharsets.UTF_8);
    String smtpMessage =
        "DATA\r\n"
            + EmailConversionUtils.duplicateDotsAtLineBegins(mimeMessage)
            + EmailConversionUtils.CRLF_DOT_CRLF;
    byte[] smtpMessageBytes = smtpMessage.getBytes(StandardCharsets.UTF_8);
    final RbelElement convertedMessage =
        converter.parseMessage(
            smtpMessageBytes,
            new RbelMessageMetadata()
                .withSender(new RbelHostname("sender", 13421))
                .withReceiver(new RbelHostname("receiver", 14512))
                .withTransmissionTime(ZonedDateTime.now()));

    final String convertedHtml = RbelHtmlRenderer.render(List.of(convertedMessage));
    FileUtils.writeStringToFile(
        new File("target/directHtml.html"), convertedHtml, StandardCharsets.UTF_8);

    assertThat(convertedHtml)
        .contains("SMTP Request")
        .contains("Command: </b>" + "DATA")
        .contains("Arguments: </b>")
        .contains("Mime Message:");
  }

  private RbelElement convertToRbelElement(String request) {
    var sender = new RbelHostname("host1", 1);
    var receiver = new RbelHostname("host2", 2);
    return convertToRbelElement(request, sender, receiver);
  }

  private RbelElement convertToRbelElement(
      String input, RbelHostname sender, RbelHostname recipient) {
    return converter.parseMessage(
        input.getBytes(StandardCharsets.UTF_8),
        new RbelMessageMetadata().withSender(sender).withReceiver(recipient));
  }
}
