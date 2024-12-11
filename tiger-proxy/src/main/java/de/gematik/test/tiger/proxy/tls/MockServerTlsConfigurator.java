/*
 *
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
 */
package de.gematik.test.tiger.proxy.tls;

import static de.gematik.test.tiger.proxy.tls.OcspUtils.buildOcspResponse;
import static de.gematik.test.tiger.proxy.tls.TlsCertificateGenerator.generateNewCaCertificate;

import de.gematik.test.tiger.TigerAgent;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerTlsConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import de.gematik.test.tiger.proxy.TigerProxyMasterSecretListener;
import de.gematik.test.tiger.proxy.exceptions.TigerProxySslException;
import io.netty.handler.ssl.SslProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLException;
import lombok.*;
import org.apache.commons.collections4.CollectionUtils;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MockServerTlsConfigurator {
  private final MockServerConfiguration mockServerConfiguration;
  private final TigerProxyConfiguration tigerProxyConfiguration;
  private Optional<TigerTlsConfiguration> tlsConfiguration;
  @Getter private TigerPkiIdentity serverRootCa;
  private final Optional<String> tigerProxyName;
  private final List<KeyAndCertificateFactory> tlsFactories = new ArrayList<>();
  private boolean usingGenericCa;

  public void execute() {
    tlsConfiguration = Optional.ofNullable(tigerProxyConfiguration.getTls());
    serverRootCa = determineServerRootCa();

    mockServerConfiguration.serverKeyAndCertificateFactory(buildServerKeyAndCertificateFactory());
    mockServerConfiguration.clientKeyAndCertificateFactory(buildClientKeyAndCertificateFactory());
    customizeSslIfApplicable();
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
            try {
              System.setProperty(
                  "jdk.tls.namedGroups",
                  String.join(",", tlsConfiguration.get().getClientSupportedGroups()));
              sslContextBuilder.sslProvider(SslProvider.JDK);
              return sslContextBuilder.build();
            } catch (SSLException e) {
              throw new TigerProxySslException(
                  "Error while building SSL context in Tiger-Proxy " + tigerProxyName.orElse(""),
                  e);
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
                    builder.sslContextProvider(new BouncyCastleJsseProvider());
                  });

          return builder;
        });
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
    } else {
      usingGenericCa = true;
      return generateNewCaCertificate();
    }
  }
}
