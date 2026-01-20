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
package de.gematik.test.tiger.proxy.tls;

import static de.gematik.test.tiger.proxy.tls.OcspUtils.buildOcspResponse;
import static de.gematik.test.tiger.proxy.tls.TlsCertificateGenerator.generateNewCaCertificate;

import de.gematik.test.tiger.TigerAgent;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAlgorithmPreference;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import de.gematik.test.tiger.proxy.TigerProxyMasterSecretListener;
import de.gematik.test.tiger.proxy.exceptions.TigerProxySslException;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Slf4j
public class MockServerTlsConfigurator {

  private static final String NAMED_GROUPS = "jdk.tls.namedGroups";
  private final MockServerConfiguration mockServerConfiguration;
  private final TigerProxyConfiguration tigerProxyConfiguration;
  private final Optional<String> tigerProxyName;
  private final List<KeyAndCertificateFactory> tlsFactories = new ArrayList<>();
  private Optional<TigerTlsConfiguration> tlsConfiguration;
  @Getter
  private TigerPkiIdentity serverRootCa;
  private boolean usingGenericCa;

  public void execute() {
    tlsConfiguration = Optional.ofNullable(tigerProxyConfiguration.getTls());
    serverRootCa = determineServerRootCa();

    mockServerConfiguration.serverKeyAndCertificateFactory(buildServerKeyAndCertificateFactory());
    mockServerConfiguration.clientKeyAndCertificateFactory(buildClientKeyAndCertificateFactory());
    customizeSslIfApplicable();
    mockServerConfiguration.keyAlgorithmPreference(determineKeyAlgorithmPreference());
  }

  private KeyAlgorithmPreference determineKeyAlgorithmPreference() {
    return tlsConfiguration
        .map(TigerTlsConfiguration::getServerSslSuites)
        .filter(CollectionUtils::isNotEmpty)
        .map(
            suites -> {
              if (suites.stream().anyMatch(suite -> suite.contains("ECDSA"))) {
                return KeyAlgorithmPreference.ECC;
              } else if (suites.stream().anyMatch(suite -> suite.contains("RSA"))) {
                return KeyAlgorithmPreference.RSA;
              } else {
                return KeyAlgorithmPreference.UNKNOWN;
              }
            })
        .orElse(KeyAlgorithmPreference.UNKNOWN);
  }

  private void customizeSslIfApplicable() {
    customizeServerBuilderCustomizer();
    customizeClientBuilderCustomizer();
    customizeClientBuilderFunction();

    tlsConfiguration
        .map(TigerTlsConfiguration::getMasterSecretsFile)
        .ifPresent(
            filename -> TigerAgent.addListener(new TigerProxyMasterSecretListener(filename)));
  }

  private void customizeClientBuilderFunction() {
    if (tlsConfiguration
        .map(TigerTlsConfiguration::getClientSupportedGroups)
        .filter(CollectionUtils::isNotEmpty)
        .isPresent()) {
      mockServerConfiguration.clientSslContextBuilderFunction(
          sslContextBuilder -> {
            val previousGroups = System.getProperty(NAMED_GROUPS);
            try {
              System.setProperty(
                  NAMED_GROUPS,
                  String.join(",", tlsConfiguration.get().getClientSupportedGroups()));
              sslContextBuilder.sslProvider(SslProvider.JDK);
              return sslContextBuilder.build();
            } catch (SSLException e) {
              throw new TigerProxySslException(
                  "Error while building SSL context in Tiger-Proxy " + tigerProxyName.orElse(""),
                  e);
            } finally {
              if (previousGroups != null) {
                System.setProperty(NAMED_GROUPS, previousGroups);
              } else {
                System.clearProperty(NAMED_GROUPS);
              }
            }
          });
    }
  }

  private KeyAndCertificateFactory buildServerKeyAndCertificateFactory() {
    boolean allowGenericFallbackIdentity =
        tlsConfiguration.map(TigerTlsConfiguration::isAllowGenericFallbackIdentity).orElse(false);
    val staticFactory = generateStaticFactory();
    if (staticFactory.isPresent()) {
      if (!usingGenericCa || allowGenericFallbackIdentity) {
        val dynamicFactory = generateDynamicFactory();
        val combinedFactory =
            new CombinedKeyAndCertificateFactory(staticFactory.get(), dynamicFactory);
        this.tlsFactories.add(combinedFactory);
        return combinedFactory;
      } else {
        this.tlsFactories.add(staticFactory.get());
        return staticFactory.get();
      }
    } else {
      val dynamicFactory = generateDynamicFactory();
      this.tlsFactories.add(dynamicFactory);
      return dynamicFactory;
    }
  }

  private Optional<KeyAndCertificateFactory> generateStaticFactory() {
    if (tlsConfiguration.map(TigerTlsConfiguration::getServerIdentity).isPresent()) {
      return Optional.of(
          new StaticKeyAndCertificateFactory(List.of(tlsConfiguration.get().getServerIdentity())));
    } else if (tlsConfiguration.map(TigerTlsConfiguration::getServerIdentities).isPresent()) {
      return Optional.of(
          new StaticKeyAndCertificateFactory(
              tlsConfiguration.get().getServerIdentities().stream()
                  .map(TigerPkiIdentity.class::cast)
                  .toList()));
    } else {
      return Optional.empty();
    }
  }

  private DynamicKeyAndCertificateFactory generateDynamicFactory() {
    return new DynamicKeyAndCertificateFactory(
        tigerProxyConfiguration, serverRootCa, mockServerConfiguration);
  }

  private KeyAndCertificateFactory buildClientKeyAndCertificateFactory() {
    if (tlsConfiguration.map(TigerTlsConfiguration::getForwardMutualTlsIdentity).isPresent()) {
      return new StaticKeyAndCertificateFactory(
          Collections.singletonList(tlsConfiguration.get().getForwardMutualTlsIdentity()));
    } else {
      return new DynamicKeyAndCertificateFactory(
          tigerProxyConfiguration,
          new TigerPkiIdentity(
              "CertificateAuthorityCertificate.pem;CertificateAuthorityPrivateKey.pem;PKCS1"),
          mockServerConfiguration);
    }
  }

  private void customizeServerBuilderCustomizer() {
    mockServerConfiguration.sslServerContextBuilderCustomizer(
        builder -> {
          tlsConfiguration
              .map(TigerTlsConfiguration::getServerSslSuites)
              .ifPresent(builder::ciphers);
          tlsConfiguration
              .map(TigerTlsConfiguration::getServerTlsProtocols)
              .ifPresent(builder::protocols);
          tlsConfiguration
              .map(TigerTlsConfiguration::getOcspSignerIdentity)
              .ifPresentOrElse(
                  ocspSignerIdentity -> {
                    builder.enableOcsp(true);
                    mockServerConfiguration.ocspResponseSupplier(
                        certificate -> buildOcspResponse(certificate, ocspSignerIdentity));
                    builder.sslProvider(SslProvider.OPENSSL);
                  },
                  () -> {
                    builder.sslProvider(SslProvider.JDK);
                    final BouncyCastleJsseProvider sslContextProvider =
                        new BouncyCastleJsseProvider();

                    builder.sslContextProvider(sslContextProvider);
                    builder.applicationProtocolConfig(
                        new ApplicationProtocolConfig(
                            Protocol.ALPN,
                            SelectorFailureBehavior.NO_ADVERTISE,
                            SelectedListenerFailureBehavior.ACCEPT,
                            List.of(ApplicationProtocolNames.HTTP_1_1)));
                  });

          return builder;
        });
  }

  @SneakyThrows
  private static List<String> extractSelectedCipherSuites(SslContextBuilder builder) {
    return builder.build().cipherSuites();
  }

  private void customizeClientBuilderCustomizer() {
    mockServerConfiguration.sslClientContextBuilderCustomizer(
        builder -> {
          tlsConfiguration
              .map(TigerTlsConfiguration::getClientSslSuites)
              .ifPresent(builder::ciphers);
          builder.sslProvider(SslProvider.JDK);
          return builder;
        });
  }

  private TigerPkiIdentity determineServerRootCa() {
    if (tigerProxyConfiguration.getTls().getServerRootCa() != null) {
      usingGenericCa = false;
      return tigerProxyConfiguration.getTls().getServerRootCa();
    } else if (serverRootCa != null) {
      usingGenericCa = true;
      return serverRootCa;
    } else {
      usingGenericCa = true;
      return generateNewCaCertificate();
    }
  }
}
