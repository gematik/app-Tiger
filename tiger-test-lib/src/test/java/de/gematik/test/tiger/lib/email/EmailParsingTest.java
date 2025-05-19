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

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.facets.pop3.RbelPop3Command;
import de.gematik.rbellogger.facets.pop3.RbelPop3CommandFacet;
import de.gematik.rbellogger.facets.pop3.RbelPop3ResponseFacet;
import de.gematik.rbellogger.facets.smtp.RbelSmtpCommandFacet;
import de.gematik.rbellogger.facets.smtp.RbelSmtpResponseFacet;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
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
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.core.ConditionTimeoutException;
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
      tigerProxy:
        proxyPort: ${free.port.0}
        directReverseProxy:
          hostname: 127.0.0.1
          port: ${free.port.1}
          ignoreConnectionErrors: true
        activateRbelParsingFor:
          - pop3""")
  @Test
  void testSendCAPACommand_ServerRespondsWithLongOkLine(TigerTestEnvMgr tigerTestEnvMgr) {

    var fakeMailServerPort =
        Integer.parseInt(TigerGlobalConfiguration.resolvePlaceholders("${free.port.1}"));

    try (var mailClientSocket =
            newClientSocketTo(tigerTestEnvMgr.getLocalTigerProxyOrFail(), true);
        var fakeMailServer = new FakeMailServer(fakeMailServerPort, true);
        var clientInputStream = mailClientSocket.getInputStream();
        var clientOutputStream = mailClientSocket.getOutputStream();
        var reader =
            new BufferedReader(
                new InputStreamReader(clientInputStream, StandardCharsets.US_ASCII))) {
      // Read welcome message
      assertThat(reader.readLine()).isEqualTo("+OK POP3 server ready");

      // Test CAPA command
      clientOutputStream.write("CAPA\r\n".getBytes(StandardCharsets.US_ASCII));
      clientOutputStream.flush();

      assertThat(reader.readLine()).isEqualTo("+OK Capability list follows");
      assertThat(reader.readLine()).isEqualTo("USER");
      assertThat(reader.readLine()).isEqualTo("PASS");
      assertThat(reader.readLine()).isEqualTo("UIDL");
      assertThat(reader.readLine()).isEqualTo(".");

      await()
          .atMost(5, TimeUnit.SECONDS)
          .until(() -> tigerTestEnvMgr.getLocalTigerProxyOrFail().getRbelMessages().size() == 3);
      tigerTestEnvMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();
      List<RbelElement> messages =
          tigerTestEnvMgr.getLocalTigerProxyOrFail().getRbelMessages().stream().toList();
      assertThat(messages).hasSize(3); // server ready + capa command + response from capa
      assertThat(messages.get(1))
          .extractChildWithPath("$.pop3Command")
          .hasValueEqualTo(RbelPop3Command.CAPA);
      assertThat(messages.get(2))
          .extractChildWithPath("$.pop3Body")
          .hasStringContentEqualTo("USER\r\nPASS\r\nUIDL");
    }
  }

  /** A fake mail server that only responds to the CAPA command* */
  @SneakyThrows
  private static void createFakeMailServer(int port, boolean okWithHeader) throws IOException {}

  @NotNull
  public static Socket newClientSocketTo(TigerProxy tigerProxy, boolean secure) throws IOException {
    if (secure) {
      return tigerProxy
          .buildSslContext()
          .getSocketFactory()
          .createSocket("localhost", tigerProxy.getProxyPort());
    } else {
      return new Socket("localhost", tigerProxy.getProxyPort());
    }
  }

  @NotNull
  @SneakyThrows
  public static ServerSocket newServerSocket(boolean secure, int port) {
    if (secure) {
      final KeyStore ks = buildTruststore();
      final TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(ks);
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(ks, "gematik".toCharArray());
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

      return sslContext.getServerSocketFactory().createServerSocket(port);
    } else {
      return new ServerSocket(port);
    }
  }

  @SneakyThrows
  private static KeyStore buildTruststore() {
    final TigerPkiIdentity serverIdentity =
        new TigerPkiIdentity("src/test/resources/rsaStoreWithChain.jks;gematik");
    return serverIdentity.toKeyStoreWithPassword("gematik");
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
     fileSaveInfo:
        writeToFile: true
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
            - smtp
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

    var message = retrieveByPopSecure(pop3sProxyPort);
    assertEqual(message, ExpectedMail.smallMessage());

    tigerTestEnvMgr.getLocalTigerProxyOrFail().waitForAllCurrentMessagesToBeParsed();
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
        activateRbelParsingFor:
          - smtp
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
                activateRbelParsingFor:
                  - smtp
                skipParsingWhenMessageLargerThanKb: 1
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
                skipParsingWhenMessageLargerThanKb: 1
           """)
  @Test
  void testSendAndReceiveEmailOverMeshTigerProxy_bigAttachment(TigerTestEnvMgr tigerTestEnvMgr) {
    startGreenMail();
    ServerSetup smtpProxy = getSmtpProxy();

    String to = "to@localhost";
    String from = "from@localhost";
    String subject = "Test Large Email";
    int attachmentSize = 1024 * 1024;
    String body = "A".repeat(attachmentSize); // 3 MB body

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
        .hasSizeGreaterThan(attachmentSize);

    try {
      await()
          .atMost(30, TimeUnit.SECONDS)
          .pollInterval(1, TimeUnit.SECONDS)
          .until(
              () -> {
                var messages =
                    tigerTestEnvMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList();
                log.info("Currently {} messages", messages.size());
                return messages.stream()
                    .filter(EmailParsingTest::hasSmtpData)
                    .map(EmailParsingTest::extractSmtpBody)
                    // we compare with endsWith because the extractedBody contains two extra
                    // initial
                    // lines added by the greenmail client
                    .anyMatch(extractedBody::endsWith);
              });
    } catch (ConditionTimeoutException e) {
      var messages = tigerTestEnvMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageList();
      messages.stream().map(RbelElement::printTreeStructure).forEach(System.out::println);
      throw e;
    }
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

    try {
      await()
          .atMost(10, TimeUnit.SECONDS)
          .until(
              () -> {
                var size =
                    tigerTestEnvMgr
                        .getLocalTigerProxyOrFail()
                        .getRbelLogger()
                        .getMessageHistory()
                        .stream()
                        .filter(
                            msg ->
                                msg.hasFacet(RbelPop3CommandFacet.class)
                                    || msg.hasFacet(RbelSmtpCommandFacet.class)
                                    || msg.hasFacet(RbelPop3ResponseFacet.class)
                                    || msg.hasFacet(RbelSmtpResponseFacet.class))
                        .count();
                log.debug("currently " + size + " messages, wanted " + expectedMessages);
                return size == expectedMessages;
              });
    } catch (ConditionTimeoutException e) {
      var messages =
          tigerTestEnvMgr.getLocalTigerProxyOrFail().getRbelLogger().getMessageHistory().stream()
              .filter(
                  msg ->
                      msg.hasFacet(RbelPop3CommandFacet.class)
                          || msg.hasFacet(RbelSmtpCommandFacet.class)
                          || msg.hasFacet(RbelPop3ResponseFacet.class)
                          || msg.hasFacet(RbelSmtpResponseFacet.class))
              .toList();
      messages.stream().map(RbelElement::printTreeStructure).forEach(System.out::println);
      throw e;
    }
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

  class FakeMailServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final Thread serverThread;

    public FakeMailServer(int port, boolean okWithHeader) {
      this.serverSocket = newServerSocket(true, port);
      this.serverThread = startServer(okWithHeader);
    }

    private Thread startServer(boolean okWithHeader) {
      var serverThread =
          new Thread(
              () -> {
                try (Socket clientSocket = serverSocket.accept();
                    BufferedWriter out =
                        new BufferedWriter(
                            new OutputStreamWriter(
                                clientSocket.getOutputStream(), StandardCharsets.US_ASCII));
                    BufferedReader in =
                        new BufferedReader(
                            new InputStreamReader(
                                clientSocket.getInputStream(), StandardCharsets.US_ASCII))) {

                  // Send welcome message
                  out.write("+OK POP3 server ready\r\n");
                  out.flush();

                  // Handle commands
                  String line;
                  while ((line = in.readLine()) != null) {
                    if (line.equalsIgnoreCase("CAPA")) {
                      if (okWithHeader) {
                        out.write("+OK Capability list follows\r\n");
                      } else {
                        out.write("+OK\r\n");
                      }
                      out.flush();
                      out.write("USER\r\n");
                      out.flush();
                      out.write("PASS\r\n");
                      out.flush();
                      out.write("UIDL\r\n");
                      out.flush();
                      out.write(".\r\n");
                    } else {
                      out.write("-ERR Unknown command\r\n");
                    }
                    out.flush();
                  }
                } catch (IOException e) {
                  e.printStackTrace();
                }
              });
      serverThread.start();
      return serverThread;
    }

    @Override
    public void close() throws Exception {
      if (serverThread != null) {
        serverThread.join(5000);
      }
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    }
  }
}
