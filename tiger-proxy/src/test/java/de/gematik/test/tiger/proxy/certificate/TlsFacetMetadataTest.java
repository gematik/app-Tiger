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
package de.gematik.test.tiger.proxy.certificate;

import static de.gematik.rbellogger.data.RbelElementAssertion.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.file.RbelFileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for TLS facet metadata handling across multiple messages in the same TLS connection. These
 * tests verify that: 1. TLS metadata is only stored in the first message of a connection 2.
 * Subsequent messages in the same connection reuse TLS information from the first message 3. All
 * messages in the connection have the complete TlsFacet attached
 */
@Slf4j
class TlsFacetMetadataTest {

  private RbelLogger rbelLogger;
  private static final String TLS_VERSION = "TLSv1.3";
  private static final String CIPHER_SUITE = "TLS_AES_256_GCM_SHA384";

  // Sample certificate for testing (base64 encoded dummy certificate)
  private static final String SAMPLE_CERT_BASE64 =
      Base64.getEncoder().encodeToString("SAMPLE_CERTIFICATE_DATA".getBytes());

  @BeforeEach
  void setUp() {
    rbelLogger = RbelLogger.build();
  }

  @Test
  void multipleMessagesOnSameTlsConnection_onlyFirstMessageShouldHaveTlsMetadata() {
    var messages =
        java.util.List.of(
            createHttpMessageWithTlsMetadata(
                "GET /first HTTP/1.1\r\nHost: example.com\r\n\r\n",
                true,
                "192.168.1.10:12345",
                "192.168.1.20:443"),
            createHttpMessageWithTlsMetadata(
                "GET /second HTTP/1.1\r\nHost: example.com\r\n\r\n",
                false,
                "192.168.1.10:12345",
                "192.168.1.20:443"),
            createHttpMessageWithTlsMetadata(
                "GET /third HTTP/1.1\r\nHost: example.com\r\n\r\n",
                false,
                "192.168.1.10:12345",
                "192.168.1.20:443"));

    // Verify metadata: first message should have it, subsequent messages should not
    for (int i = 0; i < messages.size(); i++) {
      verifyTlsMetadataPresent(messages.get(i), i == 0);
      verifyTlsFacetPresent(messages.get(i));
    }
  }

  @Test
  void multipleConnectionsWithDifferentTlsSettings_shouldHaveIndependentTlsData() {
    String tlsVersion1 = "TLSv1.2";
    String tlsVersion2 = "TLSv1.3";

    var conn1Messages =
        java.util.List.of(
            createHttpMessageWithCustomTls(
                "GET /conn1-first HTTP/1.1\r\n\r\n",
                true,
                tlsVersion1,
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "10.0.1.5:11111",
                "10.0.1.10:443"),
            createHttpMessageWithCustomTls(
                "GET /conn1-second HTTP/1.1\r\n\r\n",
                false,
                null,
                null,
                "10.0.1.5:11111",
                "10.0.1.10:443"));

    var conn2Messages =
        java.util.List.of(
            createHttpMessageWithCustomTls(
                "GET /conn2-first HTTP/1.1\r\n\r\n",
                true,
                tlsVersion2,
                "TLS_AES_256_GCM_SHA384",
                "10.0.2.5:22222",
                "10.0.2.10:443"),
            createHttpMessageWithCustomTls(
                "GET /conn2-second HTTP/1.1\r\n\r\n",
                false,
                null,
                null,
                "10.0.2.5:22222",
                "10.0.2.10:443"));

    // Verify connection 1 messages have TLSv1.2
    conn1Messages.forEach(msg -> verifyTlsVersion(msg, tlsVersion1));

    // Verify connection 2 messages have TLSv1.3
    conn2Messages.forEach(msg -> verifyTlsVersion(msg, tlsVersion2));
  }

  @Test
  void upstreamProxyScenario_onlyFirstMessageFromUpstreamShouldHaveTlsMetadata() {
    var upstreamMessages =
        java.util.List.of(
            createHttpMessageWithTlsMetadata(
                "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nFirst",
                true,
                "upstream-proxy:8080",
                "tiger-proxy:9090"),
            createHttpMessageWithTlsMetadata(
                "HTTP/1.1 200 OK\r\nContent-Length: 6\r\n\r\nSecond",
                false,
                "upstream-proxy:8080",
                "tiger-proxy:9090"),
            createHttpMessageWithTlsMetadata(
                "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nThird",
                false,
                "upstream-proxy:8080",
                "tiger-proxy:9090"));

    // Verify: First message has metadata, subsequent don't, all have facet
    for (int i = 0; i < upstreamMessages.size(); i++) {
      verifyTlsMetadataPresent(upstreamMessages.get(i), i == 0);
      verifyTlsFacetPresent(upstreamMessages.get(i));
    }
  }

  @Test
  void messageWithoutPreviousMessageInConnection_shouldUseTlsMetadataDirectly() {
    RbelElement standaloneMessage =
        createHttpMessageWithTlsMetadata(
            "GET /standalone HTTP/1.1\r\n\r\n",
            true,
            "standalone-sender:1234",
            "standalone-receiver:5678");

    verifyTlsMetadataPresent(standaloneMessage, true);
    verifyTlsFacetPresent(standaloneMessage);
  }

  @Test
  void clientCertificateChain_shouldBePreservedAcrossMessages() {
    var messages =
        java.util.List.of(
            createHttpMessageWithTlsMetadata(
                "GET /first HTTP/1.1\r\n\r\n", true, "cert-sender:9999", "cert-receiver:8888"),
            createHttpMessageWithTlsMetadata(
                "GET /second HTTP/1.1\r\n\r\n", false, "cert-sender:9999", "cert-receiver:8888"));

    // Verify both messages have client certificate chain
    messages.forEach(this::verifyCertificateChainPresent);

    // Verify certificate chains are identical
    var firstCertChain =
        messages.get(0).getFacet(TlsFacet.class).orElseThrow().getClientCertificateChain();
    var secondCertChain =
        messages.get(1).getFacet(TlsFacet.class).orElseThrow().getClientCertificateChain();

    assertThat(firstCertChain.getChildNodes()).hasSize(1);
    assertThat(secondCertChain.getChildNodes()).hasSize(1);
    assertThat(firstCertChain.getChildNodes().get(0).getRawContent())
        .isEqualTo(secondCertChain.getChildNodes().get(0).getRawContent());
  }

  @Test
  void writingMessagesToTgrFile_onlyFirstMessageShouldHaveTlsMetadataInFile(@TempDir Path tempDir)
      throws IOException {
    // Create three messages on the same TLS connection
    RbelElement firstMessage =
        createHttpMessageWithTlsMetadata(
            "GET /first HTTP/1.1\r\nHost: example.com\r\n\r\n",
            true, // include TLS metadata
            "192.168.1.100:55555",
            "192.168.1.200:443");

    RbelElement secondMessage =
        createHttpMessageWithTlsMetadata(
            "GET /second HTTP/1.1\r\nHost: example.com\r\n\r\n",
            false, // no TLS metadata
            "192.168.1.100:55555",
            "192.168.1.200:443");

    RbelElement thirdMessage =
        createHttpMessageWithTlsMetadata(
            "GET /third HTTP/1.1\r\nHost: example.com\r\n\r\n",
            false, // no TLS metadata
            "192.168.1.100:55555",
            "192.168.1.200:443");

    // Write messages to a TGR file
    RbelFileWriter fileWriter = new RbelFileWriter(rbelLogger.getRbelConverter());
    Path tgrFile = tempDir.resolve("test-tls-metadata.tgr");

    String tgrContent =
        fileWriter.convertToRbelFileString(firstMessage)
            + fileWriter.convertToRbelFileString(secondMessage)
            + fileWriter.convertToRbelFileString(thirdMessage);

    Files.writeString(tgrFile, tgrContent);

    log.info("Wrote TGR file to: {}", tgrFile);

    // Read the file back using RbelFileWriter's reading facilities
    RbelLogger readerLogger = RbelLogger.build();
    String fileContent = Files.readString(tgrFile);
    var readMessages =
        new RbelFileWriter(readerLogger.getRbelConverter())
            .convertFromRbelFile(fileContent, java.util.Optional.empty());

    assertThat(readMessages).hasSize(3);

    // Verify metadata: first message should have it, subsequent messages should not
    verifyTlsMetadataPresent(readMessages.get(0), true);
    verifyTlsMetadataPresent(readMessages.get(1), false);
    verifyTlsMetadataPresent(readMessages.get(2), false);

    // Verify all messages have TlsFacet
    readMessages.forEach(this::verifyTlsFacetPresent);
  }

  // Helper methods

  private void verifyTlsMetadataPresent(RbelElement message, boolean shouldBePresent) {
    assertThat(message.getFacet(RbelMessageMetadata.class))
        .isPresent()
        .get()
        .satisfies(
            metadata -> {
              if (shouldBePresent) {
                assertThat(TlsFacet.TLS_VERSION.getValue(metadata)).contains(TLS_VERSION);
                assertThat(TlsFacet.CIPHER_SUITE.getValue(metadata)).contains(CIPHER_SUITE);
                assertThat(TlsFacet.CLIENT_TLS_CERTIFICATE_CHAIN.getValue(metadata)).isPresent();
              } else {
                assertThat(TlsFacet.TLS_VERSION.getValue(metadata)).isEmpty();
                assertThat(TlsFacet.CIPHER_SUITE.getValue(metadata)).isEmpty();
                assertThat(TlsFacet.CLIENT_TLS_CERTIFICATE_CHAIN.getValue(metadata)).isEmpty();
              }
            });
  }

  private void verifyTlsFacetPresent(RbelElement message) {
    assertThat(message.getFacet(TlsFacet.class))
        .isPresent()
        .get()
        .satisfies(
            facet -> {
              assertThat(facet.getTlsVersion().getRawStringContent()).isEqualTo(TLS_VERSION);
              assertThat(facet.getCipherSuite().getRawStringContent()).isEqualTo(CIPHER_SUITE);
            });
  }

  private void verifyTlsVersion(RbelElement message, String expectedVersion) {
    assertThat(message.getFacet(TlsFacet.class))
        .isPresent()
        .get()
        .extracting(TlsFacet::getTlsVersion)
        .extracting(RbelElement::getRawStringContent)
        .isEqualTo(expectedVersion);
  }

  private void verifyCertificateChainPresent(RbelElement message) {
    assertThat(message.getFacet(TlsFacet.class))
        .isPresent()
        .get()
        .satisfies(
            tlsFacet -> {
              assertThat(tlsFacet.getClientCertificateChain()).isNotNull();
              assertThat(tlsFacet.getClientCertificateChain().getChildNodes()).isNotEmpty();
            });
  }

  private RbelElement createHttpMessageWithTlsMetadata(
      String httpContent, boolean includeTlsMetadata, String sender, String receiver) {
    return createHttpMessageWithCustomTls(
        httpContent, includeTlsMetadata, TLS_VERSION, CIPHER_SUITE, sender, receiver);
  }

  private RbelElement createHttpMessageWithCustomTls(
      String httpContent,
      boolean includeTlsMetadata,
      String tlsVersion,
      String cipherSuite,
      String sender,
      String receiver) {

    RbelMessageMetadata metadata = new RbelMessageMetadata();

    // Set connection information (sender and receiver define the connection)
    metadata.addMetadata(RbelMessageMetadata.MESSAGE_SENDER.getKey(), sender);
    metadata.addMetadata(RbelMessageMetadata.MESSAGE_RECEIVER.getKey(), receiver);

    // Add TLS metadata only if requested (simulates first message in connection)
    if (includeTlsMetadata && tlsVersion != null && cipherSuite != null) {
      metadata.addMetadata(TlsFacet.TLS_VERSION.getKey(), tlsVersion);
      metadata.addMetadata(TlsFacet.CIPHER_SUITE.getKey(), cipherSuite);

      // Add sample client certificate chain
      String[] certChain = new String[] {SAMPLE_CERT_BASE64};
      metadata.addMetadata(TlsFacet.CLIENT_TLS_CERTIFICATE_CHAIN.getKey(), certChain);
    }

    // Parse the message
    return rbelLogger.getRbelConverter().parseMessage(httpContent.getBytes(), metadata);
  }
}
