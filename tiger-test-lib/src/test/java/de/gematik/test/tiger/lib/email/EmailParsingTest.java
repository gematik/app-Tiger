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
 *
 */

package de.gematik.test.tiger.lib.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.facet.RbelSmtpCommandFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
class EmailParsingTest {

  ServerSetup smtpsAddress;
  ServerSetup pop3Address;
  GreenMail greenMail;
  private int smtpsProxyPort;
  private int pop3sProxyPort;

  void startGreenMail() {
    val smtpsPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders(
                "${tiger.config_ports.smtps.greenmailServerPort}"));
    val pop3sPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders(
                "${tiger.config_ports.pop3s.greenmailServerPort}"));

    smtpsProxyPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.smtps.proxy}"));

    pop3sProxyPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.pop3s.proxy}"));

    val tigerGreenmailSetup = createGreenMailServerSetup(smtpsPort, pop3sPort);
    smtpsAddress = tigerGreenmailSetup[0];
    pop3Address = tigerGreenmailSetup[1];

    greenMail = new GreenMail(ServerSetup.verbose(tigerGreenmailSetup));
    greenMail.start();
  }

  @BeforeEach
  void resetTigerConfig() {
    TigerGlobalConfiguration.reset();
  }

  @AfterEach
  void stopGreenMail() {
    if (greenMail != null) {
      greenMail.stop();
    }
  }

  @SneakyThrows
  @TigerTest(
      tigerYaml =
          """
   config_ports:
      pop3s:
       admin: ${free.port.0}
       proxy: ${free.port.1}
       greenmailServerPort: ${free.port.2}
      smtps:
       admin: ${free.port.4}
       proxy: ${free.port.5}
       greenmailServerPort: ${free.port.3}
   tigerProxy:
     proxyPort: ${tiger.config_ports.pop3s.proxy}
     directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.pop3s.greenmailServerPort}
     activateRbelParsingFor:
        - pop3
   """)
  @Test
  void testReceiveEmailOverTigerProxy(TigerTestEnvMgr tigerTestEnvMgr) {
    startGreenMail();
    GreenMailUtil.sendTextEmail(
        "to@localhost",
        "from@localhost",
        "test subject",
        "here the body of the email\r\n",
        smtpsAddress);

    var message = retrieveByPopSecure(pop3sProxyPort);
    assertEqual(message, ExpectedMail.smallMessage());

    expectReceivedMessages(tigerTestEnvMgr, 15);
  }

  @SneakyThrows
  @TigerTest(
      tigerYaml =
          """
   config_ports:
     pop3s:
       admin: ${free.port.0}
       proxy: ${free.port.1}
       greenmailServerPort: ${free.port.2}
     smtps:
       admin: ${free.port.4}
       proxy: ${free.port.5}
       greenmailServerPort: ${free.port.3}
   tigerProxy:
     proxyPort: ${tiger.config_ports.smtps.proxy}
     directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.smtps.greenmailServerPort}
     activateRbelParsingFor:
        - smtp
   servers:
     pop3sProxy:
        type: tigerProxy
        tigerProxyConfiguration:
          adminPort: ${tiger.config_ports.pop3s.admin}
          proxyPort: ${tiger.config_ports.pop3s.proxy}
          directReverseProxy:
             hostname: 127.0.0.1
             port: ${tiger.config_ports.pop3s.greenmailServerPort}
          activateRbelParsingFor:
            - pop3
            - mime
   """)
  @Test
  void testSendEmailOverTigerProxy(TigerTestEnvMgr tigerTestEnvMgr) {
    startGreenMail();
    ServerSetup smtpProxy = getSmtpProxy();
    GreenMailUtil.sendTextEmail(
        "to@localhost",
        "from@localhost",
        "test subject",
        "here the body of the email\r\n",
        smtpProxy);

    var message = retrieveByPopSecure(pop3sProxyPort); // pop3Address.getPort());
    assertEqual(message, ExpectedMail.smallMessage());

    expectReceivedMessages(tigerTestEnvMgr, 12);
  }

  @SneakyThrows
  @TigerTest(
      tigerYaml =
          """
   config_ports:
     pop3s:
       admin: ${free.port.0}
       proxy: ${free.port.1}
       greenmailServerPort: ${free.port.2}
     smtps:
       admin: ${free.port.4}
       proxy: ${free.port.5}
       greenmailServerPort: ${free.port.3}
   tigerProxy:
     trafficEndpoints:
        - http://localhost:${tiger.config_ports.pop3s.admin}
        - http://localhost:${tiger.config_ports.smtps.admin}
   servers:
     smtpsProxy:
      type: tigerProxy
      tigerProxyConfiguration:
        adminPort: ${tiger.config_ports.smtps.admin}
        proxyPort: ${tiger.config_ports.smtps.proxy}
        directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.smtps.greenmailServerPort}
        activateRbelParsingFor:
          - smtp
          - mime
     pop3sProxy:
      type: tigerProxy
      tigerProxyConfiguration:
        adminPort: ${tiger.config_ports.pop3s.admin}
        proxyPort: ${tiger.config_ports.pop3s.proxy}
        directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.pop3s.greenmailServerPort}
        activateRbelParsingFor:
          - pop3
          - mime
   """)
  @Test
  void testSendAndReceiveEmailOverMeshTigerProxy(TigerTestEnvMgr tigerTestEnvMgr) {
    startGreenMail();
    ServerSetup smtpProxy = getSmtpProxy();

    GreenMailUtil.sendTextEmail(
        "to@localhost",
        "from@localhost",
        "test subject",
        "here the body of the email\r\n",
        smtpProxy);

    var message = retrieveByPopSecure(pop3sProxyPort);
    assertEqual(message, ExpectedMail.smallMessage());

    expectReceivedMessages(tigerTestEnvMgr, 27);
  }

  @SneakyThrows
  @TigerTest(
      tigerYaml =
          """
           config_ports:
             pop3s:
               admin: ${free.port.0}
               proxy: ${free.port.1}
               greenmailServerPort: ${free.port.2}
             smtps:
               admin: ${free.port.4}
               proxy: ${free.port.5}
               greenmailServerPort: ${free.port.3}
           tigerProxy:
             trafficEndpoints:
                - http://localhost:${tiger.config_ports.pop3s.admin}
                - http://localhost:${tiger.config_ports.smtps.admin}
             activateRbelParsingFor:
                - pop3
                - smtp
                - mime
           servers:
             smtpsProxy:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${tiger.config_ports.smtps.admin}
                proxyPort: ${tiger.config_ports.smtps.proxy}
                directReverseProxy:
                   hostname: 127.0.0.1
                   port: ${tiger.config_ports.smtps.greenmailServerPort}
                rbelBufferSizeInMb: 0
                activateRbelParsing: false
             pop3sProxy:
              type: tigerProxy
              tigerProxyConfiguration:
                adminPort: ${tiger.config_ports.pop3s.admin}
                proxyPort: ${tiger.config_ports.pop3s.proxy}
                directReverseProxy:
                   hostname: 127.0.0.1
                   port: ${tiger.config_ports.pop3s.greenmailServerPort}
                activateRbelParsing: false
                rbelBufferSizeInMb: 0
           """)
  @Test
  void testSendAndReceiveEmailOverMeshTigerProxy_bigAttachment(TigerTestEnvMgr tigerTestEnvMgr) {
    startGreenMail();
    ServerSetup smtpProxy = getSmtpProxy();

    String to = "to@localhost";
    String from = "from@localhost";
    String subject = "Test Large Email";
    String body = "A".repeat(3 * 1024 * 1024); // 3 MB body

    MimeMessage message = new MimeMessage(GreenMailUtil.getSession(smtpProxy));
    message.setFrom(new InternetAddress(from));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
    message.setSubject(subject);

    MimeBodyPart textPart = new MimeBodyPart();
    textPart.setText(body);

    MimeMultipart multipart = new MimeMultipart();
    multipart.addBodyPart(textPart);

    message.setContent(multipart);

    GreenMailUtil.sendMimeMessage(message);
    MimeMessage messageRetrieved = (MimeMessage) retrieveByPopSecure(pop3sProxyPort);
    var extractedBody = GreenMailUtil.getWholeMessage(messageRetrieved);
    assertThat(extractedBody)
        .contains("Content-Type: multipart/mixed;")
        .contains("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .hasSizeGreaterThan(3 * 1024 * 1024);

    await()
        .atMost(1, TimeUnit.MINUTES)
        .until(
            () ->
                tigerTestEnvMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList().stream()
                    .filter(EmailParsingTest::hasSmtpData)
                    .map(EmailParsingTest::extractSmtpBody)
                    // we compare with endsWith because the extractedBody contains two extra initial
                    // lines added by the greenmail client
                    .anyMatch(extractedBody::endsWith));
  }

  private static boolean hasSmtpData(RbelElement e) {
    return e.getFacet(RbelSmtpCommandFacet.class)
        .map(RbelSmtpCommandFacet::getCommand)
        .filter(cmd -> "DATA".equals(cmd.getRawStringContent()))
        .isPresent();
  }

  private static String extractSmtpBody(RbelElement e) {
    return e.findElement("$.smtpBody").orElseThrow().getRawStringContent();
  }

  private static void expectReceivedMessages(
      TigerTestEnvMgr tigerTestEnvMgr, int expectedMessages) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () -> {
              var size =
                  tigerTestEnvMgr
                      .getLocalTigerProxyOrFail()
                      .getRbelLogger()
                      .getMessageHistory()
                      .size();
              log.debug("currently so many messages: " + size);
              return size == expectedMessages;
            });
  }

  private @NotNull ServerSetup getSmtpProxy() {
    return new ServerSetup(smtpsProxyPort, null, ServerSetup.PROTOCOL_SMTPS);
  }

  private ServerSetup[] createGreenMailServerSetup(int smtpsPort, int pop3sPort) {
    ServerSetup smtps = new ServerSetup(smtpsPort, null, ServerSetup.PROTOCOL_SMTPS);
    ServerSetup pop3s = new ServerSetup(pop3sPort, null, ServerSetup.PROTOCOL_POP3S);

    return new ServerSetup[] {smtps, pop3s};
  }

  @SneakyThrows
  Message retrieveByPopSecure(int portNumber) {
    // in order to go through the tiger proxy, we need to make a request using directly the java
    // mail
    // api instead of green mail. in this way we can use a custom port

    // Set the host pop3 address
    Properties properties = new Properties();

    properties.setProperty("mail.pop3s.host", "127.0.0.1");
    properties.setProperty("mail.pop3s.port", String.valueOf(portNumber));
    properties.setProperty("mail.pop3s.auth", "true");
    properties.setProperty("mail.pop3s.ssl.trust", "*");
    properties.setProperty("mail.debug", "true");

    // Get a Session object
    Session session = Session.getInstance(properties);

    // Create a Store object
    Store store = session.getStore("pop3s");
    store.connect("to@localhost", "to@localhost");

    // Create a Folder object and open the folder
    Folder folder = store.getFolder("INBOX");
    folder.open(Folder.READ_ONLY);

    // Retrieve the messages
    Message[] messages = folder.getMessages();

    // Print each message
    assertThat(messages).hasSize(1);
    // copies the message so that we can safely close the folder before making assertions
    val messageCopy = new MimeMessage((MimeMessage) messages[0]);

    // Close the folder and store
    folder.close(false);
    store.close();
    return messageCopy;
  }

  @SneakyThrows
  void assertEqual(Message actual, ExpectedMail expected) {
    assertThat(actual.getSubject()).isEqualTo(expected.subject);
    assertThat(actual.getFrom()[0]).hasToString(expected.from);
    assertThat(actual.getRecipients(Message.RecipientType.TO)[0]).hasToString(expected.to);
    assertThat(actual.getContent()).hasToString(expected.body);
  }

  record ExpectedMail(String from, String to, String subject, String body) {
    static ExpectedMail smallMessage() {
      return new ExpectedMail(
          "from@localhost", "to@localhost", "test subject", "here the body of the email\r\n");
    }
  }
}
