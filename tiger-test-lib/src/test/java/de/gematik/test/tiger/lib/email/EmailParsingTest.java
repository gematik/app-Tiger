/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.lib.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@Slf4j
class EmailParsingTest {

  ServerSetup smtpsAddress;
  ServerSetup pop3Address;
  GreenMail greenMail;

  void startGreenMail() {
    val smtpsPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders(
                "${tiger.config_ports.smtps.greenmailServerPort}"));
    val pop3sPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders(
                "${tiger.config_ports.pop3s.greenmailServerPort}"));

    val tigerGreenmailSetup = createGreenMailServerSetup(smtpsPort, pop3sPort);
    smtpsAddress = tigerGreenmailSetup[0];
    pop3Address = tigerGreenmailSetup[1];

    greenMail = new GreenMail(ServerSetup.verbose(tigerGreenmailSetup));
    greenMail.start();
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
       greenmailServerPort: ${free.port.3}
   tigerProxy:
     proxyPort: ${tiger.config_ports.pop3s.proxy}
     directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.pop3s.greenmailServerPort}
     fileSaveInfo:
         # should the cleartext http-traffic be logged to a file?
         writeToFile: true
         # configure the file name
         filename: "mail_test_local_proxy_pop3.tgr"
         # default false
         clearFileOnBoot: true
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

    // retrieve directly
    retrieveByPopSecure(pop3Address.getPort());
    // retrieve over the proxy
    retrieveByPopSecure(
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.pop3s.proxy}")));

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
     fileSaveInfo:
         # should the cleartext http-traffic be logged to a file?
         writeToFile: true
         # configure the file name
         filename: "mail_test_local_proxy_smtp.tgr"
         # default false
         clearFileOnBoot: true
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

    // retrieve directly
    retrieveByPopSecure(pop3Address.getPort());

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
     fileSaveInfo:
         # should the cleartext http-traffic be logged to a file?
         writeToFile: true
         # configure the file name
         filename: "mail_test_local_proxy_mesh.tgr"
         # default false
         clearFileOnBoot: true
   servers:
     smtpsProxy:
      type: tigerProxy
      tigerProxyConfiguration:
        adminPort: ${tiger.config_ports.smtps.admin}
        proxyPort: ${tiger.config_ports.smtps.proxy}
        directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.smtps.greenmailServerPort}
        fileSaveInfo:
          # should the cleartext http-traffic be logged to a file?
          writeToFile: true
          # configure the file name
          filename: "mail_test_local_proxy_mesh_smtp.tgr"
          # default false
          clearFileOnBoot: true
     pop3sProxy:
      type: tigerProxy
      tigerProxyConfiguration:
        adminPort: ${tiger.config_ports.pop3s.admin}
        proxyPort: ${tiger.config_ports.pop3s.proxy}
        directReverseProxy:
           hostname: 127.0.0.1
           port: ${tiger.config_ports.pop3s.greenmailServerPort}
        fileSaveInfo:
          # should the cleartext http-traffic be logged to a file?
          writeToFile: true
          # configure the file name
          filename: "mail_test_local_proxy_mesh_pop3.tgr"
          # default false
          clearFileOnBoot: true
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

    // retrieve directly
    retrieveByPopSecure(pop3Address.getPort());
    // retrieve over the proxy
    retrieveByPopSecure(
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.pop3s.proxy}")));

    expectReceivedMessages(tigerTestEnvMgr, 27);
  }

  private static void expectReceivedMessages(
      TigerTestEnvMgr tigerTestEnvMgr, int expectedMessages) {
    await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () ->
                tigerTestEnvMgr
                        .getLocalTigerProxyOrFail()
                        .getRbelLogger()
                        .getMessageHistory()
                        .size()
                    == expectedMessages);
  }

  private static @NotNull ServerSetup getSmtpProxy() {
    val smtpsProxyPort =
        Integer.parseInt(
            TigerGlobalConfiguration.resolvePlaceholders("${tiger.config_ports.smtps.proxy}"));

    return new ServerSetup(smtpsProxyPort, null, ServerSetup.PROTOCOL_SMTPS);
  }

  private ServerSetup[] createGreenMailServerSetup(int smtpsPort, int pop3sPort) {
    ServerSetup smtps = new ServerSetup(smtpsPort, null, ServerSetup.PROTOCOL_SMTPS);
    ServerSetup pop3s = new ServerSetup(pop3sPort, null, ServerSetup.PROTOCOL_POP3S);

    return new ServerSetup[] {smtps, pop3s};
  }

  @SneakyThrows
  void retrieveByPopSecure(int portNumber) {
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
    val theMessage = messages[0];
    assertThat(theMessage.getSubject()).isEqualTo("test subject");
    assertThat(theMessage.getFrom()[0].toString()).isEqualTo("from@localhost");
    assertThat(theMessage.getContent().toString()).isEqualTo("here the body of the email\r\n");

    // Close the folder and store
    folder.close(false);
    store.close();
  }
}
