/*
 * Copyright (c) 2022 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.proxy.tls;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.TigerProxy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import lombok.Builder;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.IPAddress;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;
import org.slf4j.event.Level;

public class DynamicTigerKeyAndCertificateFactory extends BCKeyAndCertificateFactory {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final TigerPkiIdentity caIdentity;
    private final MockServerLogger mockServerLogger;
    private final List<X509Certificate> certificateChain;
    private final String serverName;
    private final List<String> serverAlternativeNames;
    private TigerPkiIdentity eeIdentity;

    @Builder
    public DynamicTigerKeyAndCertificateFactory(MockServerLogger mockServerLogger,
        TigerProxyConfiguration tigerProxyConfiguration,
        TigerPkiIdentity caIdentity) {
        super(tigerProxyConfiguration.convertToMockServerConfiguration(), mockServerLogger);
        this.certificateChain = new ArrayList<>();
        this.mockServerLogger = mockServerLogger;
        this.caIdentity = caIdentity;
        this.eeIdentity = null;
        this.serverName = tigerProxyConfiguration.getTls().getDomainName();
        this.serverAlternativeNames = new ArrayList<>();
        if (tigerProxyConfiguration.getTls().getAlternativeNames() != null) {
            serverAlternativeNames.addAll(tigerProxyConfiguration.getTls().getAlternativeNames());
        }
    }

    @Override
    public boolean certificateAuthorityCertificateNotYetCreated() {
        return false;
    }

    @Override
    public X509Certificate certificateAuthorityX509Certificate() {
        buildAndSavePrivateKeyAndX509Certificate();
        if (caIdentity != null) {
            return this.caIdentity.getCertificate();
        }
        if (eeIdentity.getCertificateChain() != null
            && eeIdentity.getCertificateChain().size() > 0) {
            return eeIdentity.getCertificateChain().get(0);
        }
        return TigerProxy.DEFAULT_CA_IDENTITY.getCertificate();
    }

    @Override
    public PrivateKey privateKey() {
        buildAndSavePrivateKeyAndX509Certificate();
        return eeIdentity.getPrivateKey();
    }

    @Override
    public X509Certificate x509Certificate() {
        buildAndSavePrivateKeyAndX509Certificate();
        return eeIdentity.getCertificate();
    }

    @Override
    public void buildAndSavePrivateKeyAndX509Certificate() {
        if (eeIdentity == null) {
            try {
                KeyPair keyPair = this.generateRsaKeyPair(2048);
                X509Certificate x509Certificate =
                    this.createCertificateSignedByCa(keyPair.getPublic(), this.caIdentity.getCertificate(),
                        this.caIdentity.getPrivateKey());

                eeIdentity = new TigerPkiIdentity(x509Certificate, keyPair.getPrivate());

                certificateChain.add(x509Certificate);
                certificateChain.add(caIdentity.getCertificate());
                if (MockServerLogger.isEnabled(Level.TRACE)) {
                    this.mockServerLogger.logEvent((new LogEntry()).setLogLevel(Level.TRACE)
                        .setMessageFormat("created new X509 {} with SAN Domain Names {} and IPs {}").setArguments(
                            this.x509Certificate(),
                            Arrays.toString(ConfigurationProperties.sslSubjectAlternativeNameDomains().toArray()),
                            Arrays.toString(ConfigurationProperties.sslSubjectAlternativeNameIps().toArray())));
                }
            } catch (Exception e) {
                this.mockServerLogger.logEvent(new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while generating private key and X509 certificate")
                    .setThrowable(e));
            }
        }
    }

    @Override
    public List<X509Certificate> certificateChain() {
        buildAndSavePrivateKeyAndX509Certificate();
        return certificateChain;
    }

    private X509Certificate createCertificateSignedByCa(PublicKey publicKey, X509Certificate certificateAuthorityCert,
                                                        PrivateKey certificateAuthorityPrivateKey) throws Exception {
        X500Name issuer = new X509CertificateHolder(certificateAuthorityCert.getEncoded()).getSubject();
        X500Name subject = new X500Name("CN=" + serverName + ", O=Gematik, L=Berlin, ST=Berlin, C=DE");

        BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE)); //NOSONAR

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial, NOT_BEFORE, NOT_AFTER,
            subject, publicKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, createNewSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));

        if (!serverAlternativeNames.isEmpty()) {
            DERSequence subjectAlternativeNamesExtension = new DERSequence(
                Stream.concat(serverAlternativeNames.stream(), Stream.of(serverName))
                    .distinct()
                    .map(this::mapAlternativeNameToAsn1Encodable)
                    .toArray(ASN1Encodable[]::new));
            builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        return signTheCertificate(builder, certificateAuthorityPrivateKey);
    }

    private ASN1Encodable mapAlternativeNameToAsn1Encodable(String alternativeName) {
        if (IPAddress.isValidIPv6WithNetmask(alternativeName)
            || IPAddress.isValidIPv6(alternativeName)
            || IPAddress.isValidIPv4WithNetmask(alternativeName)
            || IPAddress.isValidIPv4(alternativeName)) {
            return new GeneralName(GeneralName.iPAddress, alternativeName);
        } else {
            return new GeneralName(GeneralName.dNSName, alternativeName);
        }
    }

    private X509Certificate signTheCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey privateKey)
        throws OperatorCreationException, CertificateException {
        ContentSigner signer;
        if (privateKey instanceof RSAPrivateKey) {
            signer = (new JcaContentSignerBuilder("SHA256WithRSAEncryption")).setProvider("BC")
                .build(privateKey);
        } else {
            signer = (new JcaContentSignerBuilder("SHA256withECDSA")).setProvider("BC")
                .build(privateKey);
        }
        return (new JcaX509CertificateConverter()).setProvider("BC").getCertificate(certificateBuilder.build(signer));
    }

    private KeyPair generateRsaKeyPair(int keySize) throws Exception {
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

    @Override
    public boolean certificateNotYetCreated() {
        return eeIdentity == null;
    }

    public void resetEeCertificate() {
        eeIdentity = null;
    }

    public void addAlternativeName(String host) {
        serverAlternativeNames.add(host);
    }
}
