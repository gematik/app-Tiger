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
import org.jetbrains.annotations.Nullable;

@Value
public class TlsFacet implements RbelFacet {

  static final RbelMetadataValue<String> TLS_VERSION =
      new RbelMetadataValue<>("tlsVersion", String.class);
  static final RbelMetadataValue<String> CIPHER_SUITE =
      new RbelMetadataValue<>("cipherSuite", String.class);
  static final RbelMetadataValue<String[]> CLIENT_TLS_CERTIFICATE_CHAIN =
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

      getPreviousMessage(msg, converter)
          .filter(m -> m.hasFacet(TlsFacet.class))
          .ifPresentOrElse(
              previousMessage -> {
                val previousTlsFacet = previousMessage.getFacet(TlsFacet.class).orElseThrow();
                val tlsVersionValue = previousTlsFacet.getTlsVersion().seekValue(String.class);
                val cipherSuiteValue = previousTlsFacet.getCipherSuite().seekValue(String.class);
                val clientCertificateChainValue =
                    Optional.ofNullable(previousTlsFacet.getClientCertificateChain())
                        .map(
                            s ->
                                s.getChildNodes().stream().map(RbelElement::getRawContent).toList())
                        .orElse(null);

                if (tlsVersionValue.isPresent() && cipherSuiteValue.isPresent()) {
                  addTlsFacets(
                      msg,
                      converter,
                      tlsVersionValue.get(),
                      cipherSuiteValue.get(),
                      clientCertificateChainValue);
                }
              },
              () -> {
                val tlsVersionValue = TLS_VERSION.getValue(metadataFacet.get());
                val cipherSuiteValue = CIPHER_SUITE.getValue(metadataFacet.get());
                val clientCertificateChainValue =
                    CLIENT_TLS_CERTIFICATE_CHAIN
                        .getValue(metadataFacet.get())
                        .map(TlsFacet::unpackageClientCertificateChain)
                        .map(Stream::toList)
                        .orElse(null);

                if (tlsVersionValue.isPresent() && cipherSuiteValue.isPresent()) {
                  addTlsFacets(
                      msg,
                      converter,
                      tlsVersionValue.get(),
                      cipherSuiteValue.get(),
                      clientCertificateChainValue);
                }
              });
    }

    private static void addTlsFacets(
        RbelElement msg,
        RbelConversionExecutor converter,
        String tlsVersion,
        String cipherSuite,
        List<byte[]> clientCertificateChainValue) {
      addTlsFacet(msg, converter, tlsVersion, cipherSuite, clientCertificateChainValue);
      msg.getFacet(TracingMessagePairFacet.class)
          .filter(pair -> pair.getResponse() == msg)
          .map(TracingMessagePairFacet::getRequest)
          .ifPresent(
              req ->
                  addTlsFacet(
                      req, converter, tlsVersion, cipherSuite, clientCertificateChainValue));
    }

    private static void addTlsFacet(
        RbelElement msg,
        RbelConversionExecutor converter,
        String tlsVersionValue,
        String cipherSuiteValue,
        List<byte[]> clientCertificateChainValue) {
      msg.addOrReplaceFacet(
          new TlsFacet(
              RbelElement.wrap(msg, tlsVersionValue),
              RbelElement.wrap(msg, cipherSuiteValue),
              buildCertificateChain(msg, converter, clientCertificateChainValue)));
    }

    private static @Nullable RbelElement buildCertificateChain(
        RbelElement msg,
        RbelConversionExecutor converter,
        List<byte[]> clientCertificateChainValue) {
      if (clientCertificateChainValue != null) {
        val chain = new RbelElement(null, msg);
        return chain.addFacet(
            RbelListFacet.builder()
                .childNodes(
                    clientCertificateChainValue.stream()
                        .map(certData -> new RbelElement(certData, chain))
                        .map(converter::convertElement)
                        .toList())
                .build());
      }
      return null;
    }
  }

  public static class WriteTlsToMetadataPlugin extends RbelConverterPlugin {

    // set high priority to run before other plugins
    public static final int PLUGIN_PRIORITY = 10;

    @Override
    public RbelConversionPhase getPhase() {
      return RbelConversionPhase.CONTENT_ENRICHMENT;
    }

    @Override
    public int getPriority() {
      return PLUGIN_PRIORITY;
    }

    @Override
    public void consumeElement(RbelElement msg, RbelConversionExecutor converter) {
      val metadataFacet = msg.getFacet(RbelMessageMetadata.class);
      if (metadataFacet.isEmpty()) {
        return;
      }

      if (getPreviousMessage(msg, converter).filter(m -> m.hasFacet(TlsFacet.class)).isEmpty()) {
        val tlsFacet = msg.getFacet(TlsFacet.class);
        val metadata = metadataFacet.get();
        tlsFacet
            .flatMap(facet -> facet.getTlsVersion().seekValue(String.class))
            .ifPresent(tlsVersion -> TLS_VERSION.putValue(metadata, tlsVersion));
        tlsFacet
            .flatMap(facet -> facet.getCipherSuite().seekValue(String.class))
            .ifPresent(cipherSuite -> CIPHER_SUITE.putValue(metadata, cipherSuite));
        tlsFacet
            .map(TlsFacet::packageClientCertificateChain)
            .ifPresent(
                clientCertificates ->
                    CLIENT_TLS_CERTIFICATE_CHAIN.putValue(metadata, clientCertificates));
      }
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
