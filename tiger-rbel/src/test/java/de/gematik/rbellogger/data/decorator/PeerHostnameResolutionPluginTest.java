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
package de.gematik.rbellogger.data.decorator;

import static de.gematik.rbellogger.testutil.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.file.RbelFileWriter;
import de.gematik.rbellogger.util.RbelSocketAddress;
import de.gematik.rbellogger.util.ReverseHostnameResolver;
import de.gematik.test.tiger.common.config.TigerConfigurationKeys;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PeerHostnameResolutionPluginTest {

  private static final String REQUEST = "GET / HTTP/1.1\r\nHost: backend\r\n\r\n";

  private final List<String> lookedUp = new ArrayList<>();

  private final ReverseHostnameResolver resolver =
      new ReverseHostnameResolver(
          address -> {
            lookedUp.add(address.getHostAddress());
            return address.getHostAddress().equals("172.18.0.12")
                ? Optional.of("popp-client.testhub_default")
                : Optional.empty();
          },
          name -> List.of(InetAddress.getByName("172.18.0.12")),
          Duration.ofMinutes(1),
          Duration.ofSeconds(1));

  @AfterEach
  void reset() {
    TigerGlobalConfiguration.deleteFromAllSources(
        TigerConfigurationKeys.RBEL_REVERSE_DNS_LOOKUP.getKey());
  }

  private RbelLogger withTestResolver(RbelLogger rbelLogger) {
    final RbelConverter converter = rbelLogger.getRbelConverter();
    converter
        .getPlugins(PeerHostnameResolutionPlugin.class::isInstance)
        .forEach(converter.getConverterPlugins()::remove);
    converter.addConverter(new PeerHostnameResolutionPlugin(resolver));
    return rbelLogger;
  }

  private RbelElement parse(String sender, String receiver) {
    return withTestResolver(RbelLogger.build())
        .getRbelConverter()
        .parseMessage(
            REQUEST.getBytes(StandardCharsets.UTF_8),
            new RbelMessageMetadata()
                .withSender(RbelSocketAddress.fromString(sender).orElseThrow())
                .withReceiver(RbelSocketAddress.fromString(receiver).orElseThrow()));
  }

  @Test
  void anUnnamedSenderShouldBeNamedAndWrittenWithItsName() {
    final RbelElement message = parse("172.18.0.12:51450", "backend/172.18.0.3:8080");

    assertThat(message)
        .hasStringContentEqualToAtPosition("$.sender.domain", "popp-client")
        .hasStringContentEqualToAtPosition("$.sender", "popp-client/172.18.0.12:51450")
        .hasStringContentEqualToAtPosition("$.receiver", "backend/172.18.0.3:8080");
    assertThat(new RbelFileWriter().convertToRbelFileString(message))
        .contains("\"senderHostname\":\"popp-client/172.18.0.12:51450\"")
        .contains("\"receiverHostname\":\"backend/172.18.0.3:8080\"");
    assertThat(lookedUp).containsExactly("172.18.0.12");
  }

  @Test
  void anAddressWithoutReverseNameShouldStayAnIp() {
    assertThat(parse("172.18.0.12:51450", "172.18.0.99:8080"))
        .hasStringContentEqualToAtPosition("$.receiver", "172.18.0.99:8080");
  }

  @Test
  void loopbackAddressesShouldNotBeLookedUp() {
    parse("127.0.0.1:51450", "localhost/127.0.0.1:8080");

    assertThat(lookedUp).isEmpty();
  }

  @Test
  void theLookupShouldBeSwitchable() {
    TigerGlobalConfiguration.putValue(
        TigerConfigurationKeys.RBEL_REVERSE_DNS_LOOKUP.getKey(), false);

    assertThat(parse("172.18.0.12:51450", "backend/172.18.0.3:8080"))
        .hasStringContentEqualToAtPosition("$.sender", "172.18.0.12:51450");
    assertThat(lookedUp).isEmpty();
  }

  @Test
  void messagesReadFromFilesShouldKeepTheirAddresses(@TempDir Path directory) throws Exception {
    final Path file = directory.resolve("traffic.tgr");
    Files.writeString(
        file,
        "{\"uuid\":\"5f0c1d2e-0000-0000-0000-000000000001\",\"senderHostname\":\"172.18.0.12:51450\","
            + "\"receiverHostname\":\"172.18.0.3:8080\",\"rawMessageContent\":\""
            + Base64.getEncoder().encodeToString(REQUEST.getBytes(StandardCharsets.UTF_8))
            + "\"}\n");
    final RbelFileReaderCapturer capturer =
        RbelFileReaderCapturer.builder().rbelFile(file.toString()).build();
    final RbelLogger rbelLogger =
        withTestResolver(RbelLogger.build(new RbelConfiguration().addCapturer(capturer)));
    capturer.initialize();
    capturer.close();

    assertThat(rbelLogger.getMessagesByOrder()).hasSize(1);
    assertThat(rbelLogger.getMessagesByOrder().get(0))
        .hasStringContentEqualToAtPosition("$.sender", "172.18.0.12:51450");
    assertThat(lookedUp).isEmpty();
  }
}
