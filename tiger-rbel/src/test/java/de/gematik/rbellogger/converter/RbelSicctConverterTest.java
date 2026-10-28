/*
 *
 * Copyright 2021-2026 gematik GmbH
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
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.core.RbelBinaryFacet;
import de.gematik.rbellogger.data.core.RbelMessageInfoFacet;
import de.gematik.rbellogger.data.core.RbelNoteFacet;
import de.gematik.rbellogger.data.core.RbelRequestFacet;
import de.gematik.rbellogger.data.core.RbelResponseFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduCommandFacet;
import de.gematik.rbellogger.facets.apdu.RbelApduResponseFacet;
import de.gematik.rbellogger.facets.asn1.RbelAsn1Facet;
import de.gematik.rbellogger.facets.pki.RbelX509CertificateFacet;
import de.gematik.rbellogger.facets.sicct.RbelEcdsaPlainSignatureFacet;
import de.gematik.rbellogger.facets.sicct.RbelSicctEnvelopeFacet;
import de.gematik.rbellogger.facets.sicct.SicctMessageType;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.rbellogger.util.RbelSocketAddress;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

class RbelSicctConverterTest {

  @SneakyThrows
  private static List<RbelElement> readMessages(String file, String... activateConversionFor) {
    final RbelFileReaderCapturer fileReaderCapturer =
        RbelFileReaderCapturer.builder().rbelFile(file).build();
    final RbelConfiguration configuration = new RbelConfiguration().addCapturer(fileReaderCapturer);
    for (String parser : activateConversionFor) {
      configuration.activateConversionFor(parser);
    }
    final RbelLogger rbelLogger = RbelLogger.build(configuration);
    fileReaderCapturer.initialize();
    fileReaderCapturer.close();
    return rbelLogger.getMessages().stream().toList();
  }

  private static RbelElement parse(String hex) {
    return RbelLogger.build(new RbelConfiguration().activateConversionFor("sicct"))
        .getRbelConverter()
        .parseMessage(Hex.decode(hex), new RbelMessageMetadata());
  }

  private static String infoLine(RbelElement message) {
    return message.getFacetOrFail(RbelMessageInfoFacet.class).getMenuInfoString();
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class TerminalCommands {

    private List<RbelElement> messages;

    @BeforeAll
    void readTraffic() {
      messages = readMessages("src/test/resources/sicctTraffic.tgr", "sicct");
    }

    @Test
    void shouldRecognizeSicctMessages() throws IOException {
      FileUtils.writeStringToFile(
          new File("target/sicctFlow.html"), RbelHtmlRenderer.render(messages));

      assertThat(messages).allMatch(msg -> msg.hasFacet(RbelSicctEnvelopeFacet.class));
    }

    @Test
    void testForBasicAttributesInSicctEnvelope() {
      final RbelElement first = messages.get(0);
      assertThat(first.findElement("$.messageType").get().seekValue())
          .contains(SicctMessageType.C_COMMAND);
      assertThat(first.findElement("$.srcOrDesAddress").get().getRawContent())
          .isEqualTo(new byte[] {0, 0});
      assertThat(first.findElement("$.sequenceNumber").get().getRawContent())
          .isEqualTo(new byte[] {1, 0x41});
      assertThat(first.findElement("$.abRfu").get().getRawContent()).isEqualTo(new byte[] {0});
      assertThat(first.findElement("$.length").get().getRawContent())
          .isEqualTo(new byte[] {0, 0, 0, 0x0e});
      assertThat(first).hasGivenValueAtPosition("$.sequenceNumber", 0x0141);
      assertThat(first).hasGivenValueAtPosition("$.length", 14L);
    }

    @Test
    void shouldNameTerminalCommands() {
      assertThat(messages.stream().filter(msg -> msg.hasFacet(RbelRequestFacet.class)))
          .map(RbelSicctConverterTest::infoLine)
          .containsOnly("SICCT INIT CT SESSION", "SICCT GET STATUS", "SICCT CLOSE CT SESSION");
    }

    @Test
    void shouldNotAddHeaderFieldsToTheCommandBody() {
      final RbelElement initCtSession = messages.get(0);

      assertThat(initCtSession)
          .hasGivenValueAtPosition("$.command.cla", "80")
          .hasGivenValueAtPosition("$.command.ins", "28")
          .doesNotHaveChildWithPath("$.command.body")
          .doesNotHaveChildWithPath("$.command.data.cla");
      assertThat(initCtSession.findRbelPathMembers("$..ins")).hasSize(1);
    }

    @Test
    void responsesShouldCarryTheStatusWord() {
      final RbelElement response =
          messages.get(0).getFacetOrFail(TracingMessagePairFacet.class).getResponse();

      assertThat(response)
          .hasFacet(RbelResponseFacet.class)
          .hasGivenValueAtPosition("$.messageType", SicctMessageType.R_RESPONSE)
          .hasStringContentEqualToAtPosition("$.response.sw.meaning", "Normal processing")
          .matchesJexlExpression("$.response.sw == '9000'");
      assertThat(messages.stream().filter(msg -> msg.hasFacet(RbelResponseFacet.class)))
          .map(RbelSicctConverterTest::infoLine)
          .containsOnly("9000 Normal processing", "6900 Command not allowed");
    }

    @Test
    void shouldPairResponsesWithinTheirConnection() {
      assertThat(messages).allMatch(msg -> msg.hasFacet(TracingMessagePairFacet.class));
      for (RbelElement message : messages) {
        final RbelElement partner =
            message
                .getFacetOrFail(TracingMessagePairFacet.class)
                .getOtherMessage(message)
                .orElseThrow();
        assertThat(partner.findElement("$.sequenceNumber").get().seekValue())
            .isEqualTo(message.findElement("$.sequenceNumber").get().seekValue());
        assertThat(partner.hasFacet(RbelRequestFacet.class))
            .isNotEqualTo(message.hasFacet(RbelRequestFacet.class));
      }
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class TerminalAuthentication {

    private List<RbelElement> messages;

    @BeforeAll
    void readTraffic() throws IOException {
      messages = readMessages("src/test/resources/sicctTerminalAuthenticateTraffic.tgr", "sicct");
      FileUtils.writeStringToFile(
          new File("target/sicctTerminalAuthenticateFlow.html"), RbelHtmlRenderer.render(messages));
    }

    private RbelElement eccCommand() {
      return messages.get(0);
    }

    private RbelElement eccResponse() {
      return messages.get(1);
    }

    private RbelElement rsaResponse() {
      return messages.get(3);
    }

    @Test
    void shouldNameTheCommandWithItsQualifier() {
      assertThat(infoLine(eccCommand())).isEqualTo("EHEALTH TERMINAL AUTHENTICATE CREATE");
      assertThat(eccCommand())
          .hasGivenValueAtPosition("$.command.name", "EHEALTH TERMINAL AUTHENTICATE")
          .hasGivenValueAtPosition("$.command.p2", "01");
    }

    @Test
    void shouldStructureTheSharedSecretAndTheDisplayMessage() {
      assertThat(eccCommand())
          .hasGivenValueAtPosition(
              "$.command.data.D4", Hex.decode("7a5e58403c202550523f165c495a4642"))
          .hasGivenValueAtPosition("$.command.data.50", "Pairing mit Testsuite?");
    }

    @Test
    void shouldPairTheResponseWithTheCommand() {
      assertThat(eccResponse().getFacetOrFail(TracingMessagePairFacet.class).getRequest())
          .isSameAs(eccCommand());
      assertThat(rsaResponse().getFacetOrFail(TracingMessagePairFacet.class).getRequest())
          .isSameAs(messages.get(2));
    }

    @Test
    void ecdsaSignatureShouldBeSplitIntoRAndS() {
      final byte[] signature =
          eccResponse().findElement("$.response.data").orElseThrow().getRawContent();
      assertThat(signature).hasSize(64);

      assertThat(eccResponse())
          .hasGivenValueAtPosition(
              "$.response.data.r", new BigInteger(1, Arrays.copyOfRange(signature, 0, 32)))
          .hasGivenValueAtPosition(
              "$.response.data.s", new BigInteger(1, Arrays.copyOfRange(signature, 32, 64)))
          .hasGivenValueAtPosition("$.response.sw", "9000");
      assertThat(eccResponse().findElement("$.response.data").orElseThrow().getNotes())
          .anyMatch(note -> note.getValue().contains("r || s in plain format"));
    }

    @Test
    void rsaSignatureShouldBeKeptAsIs() {
      final RbelElement data = rsaResponse().findElement("$.response.data").orElseThrow();
      assertThat(data.getSize()).isEqualTo(256);
      assertThat(data.hasFacet(RbelEcdsaPlainSignatureFacet.class)).isFalse();
      assertThat(data.getNotes()).anyMatch(note -> note.getValue().contains("RSA signature"));
    }

    @Test
    void responseDataShouldBeBinary() {
      final RbelElement response =
          parsePair("81AA00012AD410" + "00".repeat(16) + "5016" + "41".repeat(22) + "00", 256);
      assertThat(response.findElement("$.response.data").orElseThrow())
          .hasFacet(RbelBinaryFacet.class);
    }

    @Test
    void validateResponseShouldBeMarkedAsHash() {
      final RbelElement response = parsePair("81AA000212D510" + "00".repeat(16) + "20", 32);
      assertThat(response.findElement("$.response.data").orElseThrow().getNotes())
          .anyMatch(note -> note.getValue().contains("SHA-256"));
    }

    @Test
    void unexpectedSignatureLengthShouldBeKeptWithNote() {
      final RbelElement response =
          parsePair("81AA00012AD410" + "00".repeat(16) + "5016" + "41".repeat(22) + "00", 63);
      final RbelElement data = response.findElement("$.response.data").orElseThrow();
      assertThat(data.hasFacet(RbelEcdsaPlainSignatureFacet.class)).isFalse();
      assertThat(data.getNotes()).anyMatch(note -> note.getValue().contains("63 bytes"));
    }

    /**
     * Parses a command and a successful response with the given amount of data, returns the latter.
     */
    private RbelElement parsePair(String commandApdu, int responseDataLength) {
      final var converter =
          RbelLogger.build(new RbelConfiguration().activateConversionFor("sicct"))
              .getRbelConverter();
      converter.parseMessage(
          Hex.decode(envelope("6B", commandApdu)), connection("konnektor:51000", "kt:4742"));
      return converter.parseMessage(
          Hex.decode(envelope("83", "AB".repeat(responseDataLength) + "9000")),
          connection("kt:4742", "konnektor:51000"));
    }

    private static String envelope(String messageType, String body) {
      return messageType + "0000000100" + "%08X".formatted(body.length() / 2) + body;
    }

    private static RbelMessageMetadata connection(String sender, String receiver) {
      return new RbelMessageMetadata()
          .withSender(RbelSocketAddress.fromString(sender).orElseThrow())
          .withReceiver(RbelSocketAddress.fromString(receiver).orElseThrow());
    }
  }

  @Nested
  @TestInstance(Lifecycle.PER_CLASS)
  class CardApdus {

    private List<RbelElement> messages;

    @BeforeAll
    void readTraffic() throws IOException {
      messages = readMessages("src/test/resources/sicctPoppTraffic.tgr", "sicct", "X509");
      FileUtils.writeStringToFile(
          new File("target/sicctPoppFlow.html"), RbelHtmlRenderer.render(messages));
    }

    private RbelElement command(int index) {
      return messages.get(2 * index);
    }

    private RbelElement response(int index) {
      return messages.get(2 * index + 1);
    }

    @Test
    void shouldNameEveryCommand() {
      assertThat(messages).hasSize(40);
      assertThat(messages.stream().filter(msg -> msg.hasFacet(RbelRequestFacet.class)))
          .map(RbelSicctConverterTest::infoLine)
          .containsExactly(
              "SELECT DF.ESIGN",
              "READ BINARY SFI 06",
              "VERIFY",
              "SELECT DF.ESIGN",
              "MSE SET DST",
              "PSO COMPUTE DIGITAL SIGNATURE",
              "SELECT MF",
              "READ BINARY SFI 11",
              "READ BINARY SFI 07",
              "READ BINARY SFI 06",
              "LIST PUBLIC KEY",
              "MSE SET AT",
              "MSE SET DST",
              "PSO VERIFY CERTIFICATE",
              "MSE SET DST",
              "PSO VERIFY CERTIFICATE",
              "GENERAL AUTHENTICATE",
              "GENERAL AUTHENTICATE",
              "SELECT DF.ESIGN",
              "READ BINARY SFI 04");
    }

    @Test
    void everyResponseShouldBeASuccessfulResponseApdu() {
      for (int i = 0; i < 20; i++) {
        assertThat(response(i))
            .hasGivenValueAtPosition("$.messageType", SicctMessageType.R_RESPONSE)
            .hasStringContentEqualToAtPosition("$.response.sw.meaning", "Normal processing");
        assertThat(infoLine(response(i))).isEqualTo("9000 Normal processing");
      }
    }

    @Test
    void shouldPairCommandAndResponseBySequenceNumberAndAddress() {
      for (int i = 0; i < 20; i++) {
        assertThat(command(i).getFacetOrFail(TracingMessagePairFacet.class).getResponse())
            .isSameAs(response(i));
      }
    }

    @Test
    void shouldSplitCommandApdusAccordingToIso7816() {
      assertThat(command(0))
          .hasGivenValueAtPosition("$.srcOrDesAddress", 2)
          .hasGivenValueAtPosition("$.command.cla", "00")
          .hasGivenValueAtPosition("$.command.ins", "A4")
          .hasGivenValueAtPosition("$.command.p1", "04")
          .hasGivenValueAtPosition("$.command.p2", "0C")
          .hasGivenValueAtPosition("$.command.lc", 10)
          .hasGivenValueAtPosition("$.command.case", "3S")
          .hasGivenValueAtPosition("$.command.name", "SELECT")
          .doesNotHaveChildWithPath("$.command.le")
          .extractChildWithPath("$.command.data")
          .getContent()
          .isEqualTo(Hex.decode("A000000167455349474E"));
      assertThat(command(1))
          .hasGivenValueAtPosition("$.command.case", "2E")
          .hasGivenValueAtPosition("$.command.le", 65536)
          .doesNotHaveChildWithPath("$.command.data");
      assertThat(command(7)).hasGivenValueAtPosition("$.command.le", 256);
      assertThat(command(5))
          .hasGivenValueAtPosition("$.command.case", "4S")
          .hasGivenValueAtPosition("$.command.lc", 32)
          .hasGivenValueAtPosition("$.command.le", 256);
      assertThat(command(10))
          .hasGivenValueAtPosition("$.command.cla", "80")
          .doesNotHaveChildWithPath("$.command.cla.chaining");
    }

    @Test
    void shouldDecodeTheClassByte() {
      assertThat(command(16))
          .hasGivenValueAtPosition("$.command.cla.chaining", true)
          .hasGivenValueAtPosition("$.command.cla.secureMessaging", "NONE")
          .hasGivenValueAtPosition("$.command.cla.logicalChannel", 0);
      assertThat(command(17)).hasGivenValueAtPosition("$.command.cla.chaining", false);
    }

    @Test
    void shouldParseX509CertificatesInResponseData() {
      assertThat(response(1))
          .extractChildWithPath("$.response.data")
          .hasFacet(RbelX509CertificateFacet.class);
      assertThat(response(1))
          .extractChildWithPath("$.response.sw")
          .getContent()
          .isEqualTo(new byte[] {(byte) 0x90, 0});
    }

    @Test
    void shouldParseCardVerifiableCertificatesAsAsn1() {
      assertThat(response(8))
          .hasGivenValueAtPosition("$.response.data.7F21.7F4E.5F29", new byte[] {0x70})
          .hasGivenValueAtPosition("$.response.data.7F21.7F4E.5F25", Hex.decode("020300070301"))
          .hasGivenValueAtPosition("$.response.data.7F21.7F4E.7F49.0", "1.2.840.10045.4.3.2")
          .hasGivenValueAtPosition("$.response.data.7F21.7F4E.7F49.0.name", "ecdsaWithSHA256")
          .hasChildWithPath("$.response.data.7F21.5F37")
          .extractChildWithPath("$.response.data.7F21.7F4E.42")
          .getContent()
          .isEqualTo(Hex.decode("42084445475858870222"));
      assertThat(command(13))
          .hasChildWithPath("$.command.data.7F4E.5F20")
          .extractChildWithPath("$.command.data")
          .extractFacet(RbelAsn1Facet.class)
          .extracting(RbelAsn1Facet::getTag)
          .isNull();
    }

    @Test
    void shouldParseSeveralDataObjectsInOneDataField() {
      assertThat(response(7))
          .hasGivenValueAtPosition("$.response.data.EF.C0", Hex.decode("020000"));
      assertThat(response(10).findRbelPathMembers("$.response.data.E0")).hasSize(8);
      assertThat(response(10).findRbelPathMembers("$.response.data.E0.4F")).hasSize(8);
      assertThat(response(16)).hasChildWithPath("$.response.data.7C.85");
      assertThat(command(4))
          .hasGivenValueAtPosition("$.command.data.84", new byte[] {(byte) 0x86})
          .hasGivenValueAtPosition("$.command.data.80", new byte[] {0});
    }

    @Test
    void shouldKeepUnstructuredDataBinary() {
      assertThat(command(2))
          .extractChildWithPath("$.command.data")
          .doesNotHaveFacet(RbelAsn1Facet.class);
      assertThat(command(0))
          .extractChildWithPath("$.command.data")
          .doesNotHaveFacet(RbelAsn1Facet.class);
    }
  }

  @Nested
  class Envelopes {

    @Test
    void setStatusShouldBeRecognizedWithClass80() {
      assertThat(infoLine(parse("6b000000010000000004" + "80230000")))
          .isEqualTo("SICCT SET STATUS");
    }

    @Test
    void terminalCommandsShouldOnlyBeTriedFirstForTheCardTerminal() {
      assertThat(infoLine(parse("6b000000010000000004" + "80200000")))
          .isEqualTo("SICCT SELECT CT MODE");
      assertThat(infoLine(parse("6b000100010000000004" + "80200000"))).isEqualTo("GET PIN STATUS");
      assertThat(infoLine(parse("6b000100010000000004" + "80280000")))
          .isEqualTo("SICCT INIT CT SESSION");
    }

    @Test
    void unknownCommandsShouldShowClassAndInstruction() {
      assertThat(infoLine(parse("6b000100010000000004" + "80990000"))).isEqualTo("CLA 80 INS 99");
    }

    @Test
    void unknownMessageTypeShouldBeKeptWithNote() {
      final RbelElement message = parse("4200000001000000000290" + "00");

      assertThat(message)
          .hasFacet(RbelSicctEnvelopeFacet.class)
          .hasChildWithPath("$.body")
          .hasGivenValueAtPosition("$.messageType", "42");
      assertThat(message.getNotes())
          .extracting(RbelNoteFacet::getValue)
          .containsExactly("Unknown SICCT message type 0x42");
    }

    @Test
    void incompleteEnvelopeShouldWaitForMoreBytes() {
      final RbelElement message = parse("6b00000001000000000f" + "00a4040c0a");

      assertThat(message).doesNotHaveFacet(RbelSicctEnvelopeFacet.class);
      assertThat(message.getNotes())
          .extracting(RbelNoteFacet::getValue)
          .containsExactly("Incomplete SICCT envelope: 5 of 15 body bytes available");
      assertThat(RbelConverterPlugin.messageIsCompleteOrParsingDeactivated(message)).isFalse();
    }

    @Test
    void envelopeShouldOnlyConsumeItsDeclaredLength() {
      final RbelElement message = parse("830000000100000000029000" + "830000000200000000029000");

      assertThat(message.getSize()).isEqualTo(12);
      assertThat(RbelConverterPlugin.messageIsCompleteOrParsingDeactivated(message)).isTrue();
    }

    @Test
    void oversizedEnvelopeShouldBeKeptWithNote() {
      final RbelElement message = parse("6b000000010000ffffff" + "00a4040c");

      assertThat(message).hasFacet(RbelSicctEnvelopeFacet.class);
      assertThat(message.getNotes())
          .extracting(RbelNoteFacet::getValue)
          .containsExactly(
              "SICCT body length of 16777215 bytes exceeds the maximum of 1048576 bytes");
    }

    @Test
    void emptyBodyShouldBeKeptWithNote() {
      final RbelElement message = parse("6b000000010000000000");

      assertThat(message).hasFacet(RbelRequestFacet.class);
      assertThat(message)
          .extractChildWithPath("$.command")
          .doesNotHaveFacet(RbelApduCommandFacet.class);
      assertThat(message.findElement("$.command").get().getNotes())
          .extracting(RbelNoteFacet::getValue)
          .containsExactly("SICCT envelope without body");
    }

    @Test
    void invalidApduShouldBeKeptWithNote() {
      final RbelElement message = parse("6b000100010000000006" + "00a4040c0aa0");

      assertThat(message.findElement("$.command").get().getNotes())
          .extracting(RbelNoteFacet::getValue)
          .containsExactly("Body is not a valid ISO 7816-4 command APDU");
      assertThat(infoLine(message)).isEqualTo("invalid command APDU");
    }

    @Test
    void eventMessagesShouldBeRecognized() {
      final RbelElement message = parse("50000100010000000002" + "1234");

      assertThat(message)
          .hasGivenValueAtPosition("$.messageType", SicctMessageType.EVENT_MESSAGE)
          .hasChildWithPath("$.event")
          .doesNotHaveFacet(RbelRequestFacet.class)
          .doesNotHaveFacet(RbelResponseFacet.class);
      assertThat(RbelConverterPlugin.messageIsCompleteOrParsingDeactivated(message)).isTrue();
    }

    @Test
    void responseWithoutDataShouldOnlyHaveTheStatusWord() {
      assertThat(parse("830000000100000000026982"))
          .extractChildWithPath("$.response")
          .hasFacet(RbelApduResponseFacet.class)
          .doesNotHaveChildWithPath("$.data")
          .hasStringContentEqualToAtPosition("$.sw.meaning", "Security status not satisfied");
    }

    @Test
    void httpPostShouldNotBeMistakenForAnEvent() {
      final RbelElement message =
          parse(Hex.toHexString("POST /token HTTP/1.1\r\nContent-Length: 0\r\n\r\n".getBytes()));

      assertThat(message).doesNotHaveFacet(RbelSicctEnvelopeFacet.class);
    }
  }
}
