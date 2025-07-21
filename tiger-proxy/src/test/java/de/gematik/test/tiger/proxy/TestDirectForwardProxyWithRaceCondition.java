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
package de.gematik.test.tiger.proxy;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static de.gematik.test.tiger.proxy.PcapReplayer.client;
import static de.gematik.test.tiger.proxy.PcapReplayer.server;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import de.gematik.rbellogger.facets.http.RbelHttpRequestFacet;
import de.gematik.rbellogger.facets.http.RbelHttpResponseFacet;
import de.gematik.rbellogger.facets.pop3.RbelPop3Command;
import de.gematik.rbellogger.facets.pop3.RbelPop3CommandFacet;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseFacet;
import de.gematik.rbellogger.facets.sicct.RbelSicctCommand;
import de.gematik.rbellogger.facets.sicct.RbelSicctCommandFacet;
import de.gematik.rbellogger.facets.sicct.RbelSicctEnvelopeFacet;
import de.gematik.rbellogger.facets.sicct.RbelSicctHeaderFacet;
import de.gematik.rbellogger.facets.smtp.RbelSmtpCommandFacet;
import de.gematik.rbellogger.facets.smtp.RbelSmtpResponseFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import de.gematik.test.tiger.common.data.config.tigerproxy.DirectReverseProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyModifierDescription;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.handler.RbelBinaryModifierPlugin;
import java.io.File;
import java.nio.file.Files;
import java.security.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

@Slf4j
public class TestDirectForwardProxyWithRaceCondition {

  public static final String SMTP_COMMUNICATION =
      """
S: 220 smtp.example.com ESMTP Postfix

C: HELO relay.example.org

S: 250 Hello relay.example.org, I am glad to meet you

C: MAIL FROM:<bob@example.org>

S: 250 Ok

C: RCPT TO:<alice@example.com>

S: 250 Ok

C: RCPT TO:<theboss@example.com>

S: 250 Ok

C: DATA

S: 354 End data with <CR><LF>.<CR><LF>

C: From: "Bob Example" <bob@example.org>
C: To: "Alice Example" <alice@example.com>
C: Cc: theboss@example.com
C: Date: Tue, 15 Jan 2008 16:02:43 -0500
C: Subject: Test message
C:
C: Hello Alice.
C: This is a test message with 5 header fields and 4 lines in the message body.
C: Your friend,
C: Bob
C: .

S: 250 Ok: queued as 12345

C: QUIT

S: 221 Bye
      """;
  public static final String POP3_COMMUNICATION =
      """
S: +OK POP3 server ready <1896.697170952@dbc.mtview.ca.us>
C: AUTH PLAIN
S: +\s
C: dGVzdAB0ZXN0AHRlc3Q=
S: +OK Maildrop locked and ready
C: AUTH PLAIN dGVzdAB0ZXN0AHRlc3Q=
S: +OK Maildrop locked and ready
      """;

  @SneakyThrows
  @Test
  public void cetpReplayWithRandomTcpChunks() {
    final PcapReplayer replayer =
        new PcapReplayer("src/test/resources/stapelsignatur_log.pcapng", 50294, 7001, false)
            .readReplay();
    val tigerProxy = replayer.replayWithDirectForwardUsing(new TigerProxyConfiguration());

    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    try {
      waitForMessages(tigerProxy, 68);
    } catch (ConditionTimeoutException e) {
      log.warn("Timeout while waiting for messages, but continuing anyway");
    }

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/cetpReplay.html").toPath(), html.getBytes());
  }

  @SneakyThrows
  @Test
  public void testSicctHandshake() {
    final PcapReplayer replayer =
        new PcapReplayer("src/test/resources/sicctExtended.json", 55648, 4741, true).readReplay();
    // new PcapReplayer("src/test/resources/sicctHandshakeDecryptedPcap.json", 53406, 4741, false)
    //    .readReplay();
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            TigerProxyConfiguration.builder()
                .directReverseProxy(
                    DirectReverseProxyInfo.builder()
                        .modifierPlugins(
                            List.of(
                                TigerProxyModifierDescription.builder()
                                    .name("SicctPairingFaker")
                                    .parameters(
                                        Map.of(
                                            "signerIdentity",
                                            "src/test/resources/eccServerCertificate.p12"))
                                    .build()))
                        .build())
                .activateRbelParsingFor(List.of("sicct"))
                .build());

    tigerProxy.waitForAllCurrentMessagesToBeParsed();
    waitForMessages(tigerProxy, 14);

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/sicct.html").toPath(), html.getBytes());

    assertThat(tigerProxy.getRbelMessagesList()).hasSize(14);
  }

  @Data
  public static class SicctPairingFaker implements RbelBinaryModifierPlugin {
    private TigerPkiIdentity signerIdentity;

    @Override
    public Optional<byte[]> modify(RbelElement target, RbelConverter converter) {
      if (isResponseToPairingRequest(target)) {
        return extractPairingSecretFromRequest(target)
            .flatMap(this::generateSignature)
            .flatMap(signature -> repackSignatureIntoMessage(signature, target));
      } else {
        return Optional.empty();
      }
    }

    @SneakyThrows
    private Optional<byte[]> repackSignatureIntoMessage(
        byte @NotNull [] bytes, RbelElement originalResponse) {
      val seq =
          (org.bouncycastle.asn1.ASN1Sequence)
              org.bouncycastle.asn1.ASN1Primitive.fromByteArray(bytes);
      var part1 = ((org.bouncycastle.asn1.ASN1Integer) seq.getObjectAt(0)).getValue().toByteArray();
      var part2 = ((org.bouncycastle.asn1.ASN1Integer) seq.getObjectAt(1)).getValue().toByteArray();
      part1 = Arrays.copyOfRange(part1, part1.length - 32, part1.length);
      part2 = Arrays.copyOfRange(part2, part2.length - 32, part2.length);
      final byte[] resultingContent =
          org.bouncycastle.util.Arrays.concatenate(
              originalResponse.getContent().toByteArray(0, 10), part1, part2, Hex.decode("9000"));
      log.info(
          "Changing content of pairing request to ({} bytes, was {} bytes) {}",
          resultingContent.length,
          originalResponse.getRawContent().length,
          Hex.toHexString(resultingContent));
      return Optional.ofNullable(resultingContent);
      // return Optional.of("{'foo':'bar'}".getBytes());
    }

    private Optional<byte[]> generateSignature(byte[] message) {
      try {
        Signature signer = Signature.getInstance("SHA256withECDSA", "BC");
        signer.initSign(signerIdentity.getPrivateKey());
        signer.update(message);
        return Optional.ofNullable(signer.sign());
      } catch (Exception e) {
        log.warn("Error while signing pairing secret '{}'", Hex.toHexString(message), e);
        return Optional.empty();
      }
    }

    private @NotNull Optional<byte[]> extractPairingSecretFromRequest(RbelElement target) {
      val request =
          target
              .getFacet(TracingMessagePairFacet.class)
              .map(TracingMessagePairFacet::getRequest)
              .get();
      log.info("Found pairing request: {}", request.printTreeStructure());
      if (checkValue(request, "p1", (byte) 0) && checkValue(request, "p2", (byte) 1)) {
        log.info("Mode is correct, extracting shared secret");
        return request
            .findElement("$.command.body")
            .filter(el -> el.getContent().startsWith(Hex.decodeStrict("2ad410")))
            .map(el -> el.getContent().toByteArray(3, 0x10 + 3));
      } else {
        log.warn("Request does not match expected values for pairing request");
        return Optional.empty();
      }
    }

    private boolean isResponseToPairingRequest(RbelElement target) {
      if (!target.hasFacet(RbelSicctEnvelopeFacet.class)) {
        return false;
      }
      return target
          .getFacet(TracingMessagePairFacet.class)
          .map(TracingMessagePairFacet::getRequest)
          .flatMap(req -> req.getFacet(RbelSicctEnvelopeFacet.class))
          .map(RbelSicctEnvelopeFacet::getCommand)
          .flatMap(el -> el.getFacet(RbelSicctCommandFacet.class))
          .map(RbelSicctCommandFacet::getHeader)
          .flatMap(el -> el.getFacet(RbelSicctHeaderFacet.class))
          .map(RbelSicctHeaderFacet::getCommand)
          .filter(type -> type == RbelSicctCommand.EHEALTH_TERMINAL_AUTHENTICATE)
          .isPresent();
    }

    private static boolean checkValue(RbelElement target, String rbelPath, byte x) {
      return target
          .findElement("$.command.header." + rbelPath)
          .flatMap(el -> el.seekValue(Byte.class))
          .filter(
              value -> {
                if (value == x) return true;
                else {
                  log.warn("Expected value {} for {}, but got {}", x, rbelPath, value);
                  return false;
                }
              })
          .isPresent();
    }

    public String toString() {
      return "MyBinaryModifier{" + '}';
    }
  }

  @SneakyThrows
  @Test
  public void httpReplayWithRandomTcpChunks() {
    final PcapReplayer replayer =
        new PcapReplayer("src/test/resources/stapelsignatur_log.pcapng", 53335, 80, false)
            .readReplay();
    val tigerProxy = replayer.replayWithDirectForwardUsing(new TigerProxyConfiguration());

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pcapReplayHttp.html").toPath(), html.getBytes());
    var messages =
        tigerProxy.getRbelMessagesList().stream()
            .filter(
                msg ->
                    msg.hasFacet(RbelHttpRequestFacet.class)
                        || msg.hasFacet(RbelHttpResponseFacet.class))
            .toList();

    assertThat(messages).hasSize(16);
    assertThat(messages.get(0)).extractChildWithPath("$.body").getContent().hasSize(0);
    assertThat(messages.get(1)).extractChildWithPath("$.body").getContent().hasSize(14597);
  }

  @SneakyThrows
  @Test
  public void pop3_capaSplitsServerReadyMessage() {
    val replayer =
        PcapReplayer.writeReplay(
            List.of(
                server("+OK POP3 server re"),
                client("CAPA\r\n"),
                server("ady <1896.697170952@dbc.mtview.ca.us>\r\n"),
                server("+OK\r\n"),
                server("UIDL\r\n"),
                server("blubsblab\r\n"),
                server(".\r\n")));
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("pop3", "mime")));

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pop3Replay.html").toPath(), html.getBytes());
    assertThat(
            tigerProxy.getRbelMessagesList().stream()
                .filter(TestDirectForwardProxyWithRaceCondition::isPop3Message))
        .hasSize(3); // flaky! wait for correct number
  }

  @SneakyThrows
  @Test
  public void pop3_capaPrecedesServerReadyMessage() {
    val replayer =
        PcapReplayer.writeReplay(
            List.of(
                client("CAPA\r\n"),
                server("+OK POP3 server re"),
                server("ady <1896.697170952@dbc.mtview.ca.us>\r\n"),
                server("+OK\r\n"),
                server("UIDL\r\n"),
                server("blubsblab\r\n"),
                server(".\r\n")));
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("pop3", "mime")));

    waitForMessages(tigerProxy, 3);

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pop3Replay.html").toPath(), html.getBytes());

    assertThat(
            tigerProxy.getRbelMessagesList().stream()
                .filter(TestDirectForwardProxyWithRaceCondition::isPop3Message))
        .hasSize(3);
  }

  @SneakyThrows
  @Test
  public void pop3_quitOvertakesRetrResponse() {
    val replayer =
        PcapReplayer.writeReplay(
            List.of(
                server("+OK POP3 server ready <1896.697170952@dbc.mtview.ca.us>\r\n"),
                client("retr 1\r\n"),
                client("QUIT\r\n"),
                server("+OK body follows\r\n"),
                server("MailBody\r\n"),
                server(".\r\n"),
                server("+OK bye\r\n")));
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("pop3", "mime")));

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pop3Replay.html").toPath(), html.getBytes());

    waitForMessages(tigerProxy, 5);

    var messages =
        tigerProxy.getRbelMessagesList().stream()
            .filter(TestDirectForwardProxyWithRaceCondition::isPop3Message)
            .toList();
    assertThat(messages).hasSize(5);

    RbelElement retrCommand = messages.get(1);
    assertThat(retrCommand)
        .extractChildWithPath("$.pop3Command")
        .hasValueEqualTo(RbelPop3Command.RETR);
    RbelElement quitCommand = messages.get(2);
    assertThat(quitCommand)
        .extractChildWithPath("$.pop3Command")
        .hasValueEqualTo(RbelPop3Command.QUIT);
    RbelElement retrResponse = messages.get(3);
    assertThat(retrResponse.getFacetOrFail(TracingMessagePairFacet.class).getRequest())
        .isEqualTo(retrCommand);
    RbelElement quitResponse = messages.get(4);
    assertThat(quitResponse.getFacetOrFail(TracingMessagePairFacet.class).getRequest())
        .isEqualTo(quitCommand);
  }

  @SneakyThrows
  private static void waitForMessages(TigerProxy tigerProxy, int expectedMessages) {
    try {
      //      Awaitility.await()
      //          .pollThread(Thread::new)
      //          .atMost(2, TimeUnit.SECONDS)
      //          .until(tigerProxy.getRbelMessagesList()::size, size -> size == expectedMessages);
      long endTime = System.currentTimeMillis() + 2000_00;
      while (tigerProxy.getRbelMessagesList().size() != expectedMessages
          && System.currentTimeMillis() < endTime) {
        Thread.sleep(100);
      }
      if (tigerProxy.getRbelMessagesList().size() != expectedMessages) {
        throw new ConditionTimeoutException(
            "Timeout waiting for expected messages, wanted "
                + expectedMessages
                + " but got "
                + tigerProxy.getRbelMessagesList().size());
      }
    } catch (ConditionTimeoutException ex) {
      tigerProxy.getRbelMessagesList().stream()
          .map(RbelElement::printTreeStructure)
          .forEach(System.out::println);
      throw ex;
      //    } catch (InterruptedException e) {
      //      throw new RuntimeException(e);
    }
  }

  private static boolean isPop3Message(RbelElement msg) {
    return msg.hasFacet(RbelPop3CommandFacet.class) || msg.hasFacet(RbelPop3ResponseFacet.class);
  }

  @SneakyThrows
  @Test
  public void textBlobDemonstratorTest() {
    val replayer = PcapReplayer.writeReplay(SMTP_COMMUNICATION);
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("smtp", "mime")));

    waitForMessages(tigerProxy, 14);

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/mailReplay.html").toPath(), html.getBytes());
    assertThat(
            tigerProxy.getRbelMessagesList().stream()
                .filter(
                    msg ->
                        msg.hasFacet(RbelSmtpCommandFacet.class)
                            || msg.hasFacet(RbelSmtpResponseFacet.class)))
        .hasSize(14);
  }

  @SneakyThrows
  @Test
  public void testSmtpRequestPairing() {
    val replayer = PcapReplayer.writeReplay(SMTP_COMMUNICATION);
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("smtp")));

    assertThat(
            tigerProxy.getRbelMessagesList().stream()
                .filter(msg -> msg.hasFacet(RbelSmtpCommandFacet.class))
                .allMatch(msg -> msg.hasFacet(TracingMessagePairFacet.class)))
        .isTrue();
  }

  @SneakyThrows
  @Test
  public void testMultilinePop3AuthPlain() {
    val replayer = PcapReplayer.writeReplay(POP3_COMMUNICATION);
    val tigerProxy =
        replayer.replayWithDirectForwardUsing(
            new TigerProxyConfiguration().setActivateRbelParsingFor(List.of("pop3")));

    final String html = RbelHtmlRenderer.render(tigerProxy.getRbelMessagesList());
    Files.write(new File("target/pop3.html").toPath(), html.getBytes());
    assertThat(
            tigerProxy.getRbelMessagesList().stream()
                .filter(TestDirectForwardProxyWithRaceCondition::isPop3Message))
        .hasSize(5);
  }
}
