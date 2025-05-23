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

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.rbellogger.data.RbelMessageMetadata;
import de.gematik.rbellogger.data.RbelMessageMetadata.RbelMetadataValue;
import de.gematik.rbellogger.data.RbelMultiMap;
import de.gematik.rbellogger.data.core.RbelFacet;
import de.gematik.rbellogger.data.core.RbelListFacet;
import de.gematik.rbellogger.data.core.TracingMessagePairFacet;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Value;
import lombok.val;

@Value
public class TlsFacet implements RbelFacet {

  private static final RbelMetadataValue<String> TLS_VERSION =
      new RbelMetadataValue<>("tlsVersion", String.class);
  private static final RbelMetadataValue<String> CIPHER_SUITE =
      new RbelMetadataValue<>("cipherSuite", String.class);
  private static final RbelMetadataValue<String[]> CLIENT_TLS_CERTIFICATE_CHAIN =
      new RbelMetadataValue<>("clientTlsCertificateChain", String[].class);

  RbelElement tlsVersion;
  RbelElement cipherSuite;
  RbelElement clientCertificateChain;
  RbelMultiMap<RbelElement> childElements;

  public TlsFacet(
      RbelElement tlsVersion, RbelElement cipherSuite, RbelElement clientCertificateChain) {
    this.tlsVersion = tlsVersion;
    this.cipherSuite = cipherSuite;
    this.clientCertificateChain = clientCertificateChain;
    childElements =
        new RbelMultiMap<RbelElement>()
            .with(TLS_VERSION.getKey(), tlsVersion)
            .with(CIPHER_SUITE.getKey(), cipherSuite)
            .withSkipIfNull(CLIENT_TLS_CERTIFICATE_CHAIN.getKey(), clientCertificateChain);
  }

  public static class ReadTlsFromMetadataPlugin extends RbelConverterPlugin {

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.PROTOCOL_PARSING;
    }

    @Override
    public void consumeElement(RbelElement msg, RbelConversionExecutor converter) {
      val metadataFacet = msg.getFacet(RbelMessageMetadata.class);
      if (metadataFacet.isEmpty()) {
        return;
      }

      val tlsVersionValue = TLS_VERSION.getValue(metadataFacet.get());
      val cipherSuiteValue = CIPHER_SUITE.getValue(metadataFacet.get());
      if (tlsVersionValue.isPresent() && cipherSuiteValue.isPresent()) {
        msg.addOrReplaceFacet(
            new TlsFacet(
                RbelElement.wrap(msg, tlsVersionValue.get()),
                RbelElement.wrap(msg, cipherSuiteValue.get()),
                null));
        msg.getFacet(TracingMessagePairFacet.class)
            .filter(pair -> pair.getResponse() == msg)
            .map(TracingMessagePairFacet::getRequest)
            .ifPresent(
                req ->
                    req.addOrReplaceFacet(
                        new TlsFacet(
                            RbelElement.wrap(req, tlsVersionValue.get()),
                            RbelElement.wrap(req, cipherSuiteValue.get()),
                            null)));
      }

      CLIENT_TLS_CERTIFICATE_CHAIN
          .getValue(metadataFacet.get())
          .ifPresent(
              clientCertificates -> {
                val chain = new RbelElement(null, msg);
                chain.addFacet(
                    RbelListFacet.builder()
                        .childNodes(
                            unpackageClientCertificateChain(clientCertificates)
                                .map(certData -> new RbelElement(certData, chain))
                                .map(converter::convertElement)
                                .toList())
                        .build());

                msg.addOrReplaceFacet(
                    new TlsFacet(
                        msg.getFacet(TlsFacet.class).orElseThrow().getTlsVersion(),
                        msg.getFacet(TlsFacet.class).orElseThrow().getCipherSuite(),
                        chain));
              });
    }
  }

  public static class WriteTlsToMetadataPlugin extends RbelConverterPlugin {

    private static final int SLIGHTLY_HIGHER_THEN_NORMAL_PRIORITY = 10;

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_ENRICHMENT;
    }

    @Override
    public int getPriority() {
      return SLIGHTLY_HIGHER_THEN_NORMAL_PRIORITY;
    }

    @Override
    public void consumeElement(RbelElement msg, RbelConversionExecutor converter) {
      val metadataFacet = msg.getFacet(RbelMessageMetadata.class);
      if (metadataFacet.isEmpty()) {
        return;
      }

      msg.getFacet(TlsFacet.class)
          .flatMap(facet -> facet.getTlsVersion().seekValue(String.class))
          .ifPresent(tlsVersion -> TLS_VERSION.putValue(metadataFacet.get(), tlsVersion));
      msg.getFacet(TlsFacet.class)
          .flatMap(facet -> facet.getCipherSuite().seekValue(String.class))
          .ifPresent(cipherSuite -> CIPHER_SUITE.putValue(metadataFacet.get(), cipherSuite));
      // TODO only write if previous message did not already store it
      msg.getFacet(TlsFacet.class)
          .map(TlsFacet::packageClientCertificateChain)
          .ifPresent(
              clientCertificates ->
                  CLIENT_TLS_CERTIFICATE_CHAIN.putValue(metadataFacet.get(), clientCertificates));
    }
  }

  private static String[] packageClientCertificateChain(TlsFacet tlsFacet) {
    return Optional.of(tlsFacet)
        .map(TlsFacet::getClientCertificateChain)
        .map(RbelElement::getChildNodes)
        .stream()
        .flatMap(List::stream)
        .map(RbelElement::getRawContent)
        .map(Base64.getEncoder()::encodeToString)
        .toArray(String[]::new);
  }

  private static Stream<byte[]> unpackageClientCertificateChain(String[] certificates) {
    return Stream.of(certificates).map(Base64.getDecoder()::decode);
  }
}
