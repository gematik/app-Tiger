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

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.common.util.TigerSecurityProviderInitialiser;
import de.gematik.test.tiger.mockserver.configuration.MockServerConfiguration;
import de.gematik.test.tiger.mockserver.socket.tls.KeyAndCertificateFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.IPAddress;

@Slf4j
public class DynamicKeyAndCertificateFactory implements KeyAndCertificateFactory {

  static {
    TigerSecurityProviderInitialiser.initialize();
  }

  private static final Duration MAXIMUM_VALIDITY = Duration.ofDays(397);

  private final TigerPkiIdentity caIdentity;
  private final String serverName;
  private final Set<String> serverAlternativeNames;
  private TigerPkiIdentity eeIdentity;
  private List<String> hostsCoveredByGeneratedIdentity = List.of();
  private final MockServerConfiguration mockServerConfiguration;

  public DynamicKeyAndCertificateFactory(
      @NonNull TigerProxyConfiguration tigerProxyConfiguration,
      @NonNull TigerPkiIdentity caIdentity,
      @NonNull MockServerConfiguration mockServerConfiguration) {
    this.caIdentity = caIdentity;
    this.eeIdentity = null;
    this.serverName = tigerProxyConfiguration.getTls().getDomainName();
    this.serverAlternativeNames = new ConcurrentSkipListSet<>();
    if (tigerProxyConfiguration.getTls().getAlternativeNames() != null) {
      serverAlternativeNames.addAll(tigerProxyConfiguration.getTls().getAlternativeNames());
    }
    this.mockServerConfiguration = mockServerConfiguration;
  }

  @Override
  public Optional<TigerPkiIdentity> findExactIdentityForHostname(String hostname) {
    return Optional.of(resolveIdentityForHostname(hostname));
  }

  @Override
  public TigerPkiIdentity resolveIdentityForHostname(String hostname) {
    assureCurrentCertificateCoversAllNecessaryHosts();
    if (eeIdentity == null) {
      return generateNewIdentity();
    } else {
      return eeIdentity;
    }
  }

  private TigerPkiIdentity generateNewIdentity() {
    try {
      KeyPair keyPair = this.generateRsaKeyPair(2048);
      X509Certificate x509Certificate =
          this.createCertificateSignedByCa(
              keyPair.getPublic(),
              this.caIdentity.getCertificate(),
              this.caIdentity.getPrivateKey());

      eeIdentity = new TigerPkiIdentity(x509Certificate, keyPair.getPrivate());
      return eeIdentity;
    } catch (RuntimeException
        | GeneralSecurityException
        | IOException
        | OperatorCreationException e) {
      throw new IllegalArgumentException(
          "exception while generating private key and X509 certificate", e);
    }
  }

  private void assureCurrentCertificateCoversAllNecessaryHosts() {
    for (String hostThatShouldBePresent :
        mockServerConfiguration.sslSubjectAlternativeNameDomains()) {
      if (!hostsCoveredByGeneratedIdentity.contains(hostThatShouldBePresent)) {
        eeIdentity = null;
        return;
      }
    }
  }

  private X509Certificate createCertificateSignedByCa(
      PublicKey publicKey,
      X509Certificate certificateAuthorityCert,
      PrivateKey certificateAuthorityPrivateKey)
      throws GeneralSecurityException, IOException, OperatorCreationException {
    X500Name issuer = new X509CertificateHolder(certificateAuthorityCert.getEncoded()).getSubject();
    X500Name subject = new X500Name("CN=" + serverName + ", O=Gematik, L=Berlin, ST=Berlin, C=DE");

    BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE)); // NOSONAR

    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            issuer,
            serial,
            Date.from(ZonedDateTime.now().minusDays(10).toInstant()),
            Date.from(ZonedDateTime.now().plus(MAXIMUM_VALIDITY).minusDays(10).toInstant()),
            subject,
            publicKey);
    builder.addExtension(
        Extension.subjectKeyIdentifier, false, createNewSubjectKeyIdentifier(publicKey));
    builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

    hostsCoveredByGeneratedIdentity = new CopyOnWriteArrayList<>();
    hostsCoveredByGeneratedIdentity.addAll(serverAlternativeNames);
    hostsCoveredByGeneratedIdentity.addAll(
        mockServerConfiguration.sslSubjectAlternativeNameDomains());
    hostsCoveredByGeneratedIdentity.add(serverName);
    DERSequence subjectAlternativeNamesExtension =
        new DERSequence(
            hostsCoveredByGeneratedIdentity.stream()
                .distinct()
                .filter(Objects::nonNull)
                .map(this::mapAlternativeNameToAsn1Encodable)
                .toArray(ASN1Encodable[]::new));
    builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);

    return signTheCertificate(builder, certificateAuthorityPrivateKey);
  }

  private ASN1Encodable mapAlternativeNameToAsn1Encodable(String alternativeName) {
    if (IPAddress.isValidIPv6WithNetmask(alternativeName)
        || IPAddress.isValidIPv6(alternativeName)
        || IPAddress.isValidIPv4WithNetmask(alternativeName)
        || IPAddress.isValidIPv4(alternativeName)) {
      log.info("IP {}", alternativeName);
      return new GeneralName(GeneralName.iPAddress, alternativeName);
    } else {
      log.info("DNS {}", alternativeName);
      return new GeneralName(GeneralName.dNSName, alternativeName);
    }
  }

  private X509Certificate signTheCertificate(
      X509v3CertificateBuilder certificateBuilder, PrivateKey privateKey)
      throws OperatorCreationException, CertificateException {
    ContentSigner signer;
    if (privateKey instanceof RSAPrivateKey) {
      signer =
          (new JcaContentSignerBuilder("SHA256WithRSAEncryption"))
              .setProvider("BC")
              .build(privateKey);
    } else {
      signer = (new JcaContentSignerBuilder("SHA256withECDSA")).setProvider("BC").build(privateKey);
    }
    return (new JcaX509CertificateConverter())
        .setProvider("BC")
        .getCertificate(certificateBuilder.build(signer));
  }

  private KeyPair generateRsaKeyPair(int keySize) throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
    generator.initialize(keySize, new SecureRandom());
    return generator.generateKeyPair();
  }

  private SubjectKeyIdentifier createNewSubjectKeyIdentifier(Key key) throws IOException {
    try (ASN1InputStream is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()))) {
      ASN1Sequence seq = (ASN1Sequence) is.readObject();
      SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
      return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
    }
  }

  public void addAlternativeName(String host) {
    serverAlternativeNames.add(host);
  }
}
