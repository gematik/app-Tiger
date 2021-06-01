package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.util.RbelPkiIdentity;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
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
import org.mockserver.socket.tls.PEMToFile;
import org.mockserver.socket.tls.bouncycastle.BCKeyAndCertificateFactory;
import org.mockserver.socket.tls.jdk.CertificateSigningRequest;
import org.slf4j.event.Level;

public class TigerKeyAndCertificateFactory extends BCKeyAndCertificateFactory {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private final MockServerLogger mockServerLogger;
    private final RbelPkiIdentity caIdentity;
    private RbelPkiIdentity eeIdentity;

    public TigerKeyAndCertificateFactory(MockServerLogger mockServerLogger,
        TigerProxyConfiguration tigerProxyConfiguration) {
        super(mockServerLogger);
        this.mockServerLogger = mockServerLogger;
        this.caIdentity = tigerProxyConfiguration.getServerRootCa();
    }

    public boolean certificateAuthorityCertificateNotYetCreated() {
        return false;
    }

    public X509Certificate certificateAuthorityX509Certificate() {
        return this.caIdentity.getCertificate();
    }

    public PrivateKey privateKey() {
        return eeIdentity.getPrivateKey();
    }

    public X509Certificate x509Certificate() {
        return eeIdentity.getCertificate();
    }

    public void buildAndSavePrivateKeyAndX509Certificate() {
        try {
            KeyPair keyPair = this.generateKeyPair(2048);
            X509Certificate x509Certificate =
                this.createCASignedCert(keyPair.getPublic(), this.caIdentity.getCertificate(),
                    this.caIdentity.getPrivateKey(), this.caIdentity.getCertificate().getPublicKey(),
                    ConfigurationProperties.sslCertificateDomainName(),
                    ConfigurationProperties.sslSubjectAlternativeNameDomains(),
                    ConfigurationProperties.sslSubjectAlternativeNameIps());

            eeIdentity = new RbelPkiIdentity(x509Certificate, keyPair.getPrivate(), Optional.empty());

            if (MockServerLogger.isEnabled(Level.TRACE)) {
                this.mockServerLogger.logEvent((new LogEntry()).setLogLevel(Level.TRACE)
                    .setMessageFormat("created new X509{}with SAN Domain Names{}and IPs{}").setArguments(
                        new Object[]{this.x509Certificate(),
                            Arrays.toString(ConfigurationProperties.sslSubjectAlternativeNameDomains()),
                            Arrays.toString(ConfigurationProperties.sslSubjectAlternativeNameIps())}));
            }
        } catch (Exception e) {
            this.mockServerLogger.logEvent(new LogEntry()
                .setLogLevel(Level.ERROR)
                .setMessageFormat("exception while generating private key and X509 certificate")
                .setThrowable(e));
        }
    }

    private X509Certificate createCASignedCert(PublicKey publicKey, X509Certificate certificateAuthorityCert,
        PrivateKey certificateAuthorityPrivateKey, PublicKey certificateAuthorityPublicKey, String domain,
        String[] subjectAlternativeNameDomains, String[] subjectAlternativeNameIps) throws Exception {
        X500Name issuer = (new X509CertificateHolder(certificateAuthorityCert.getEncoded())).getSubject();
        X500Name subject = new X500Name("CN=" + domain + ", O=MockServer, L=London, ST=England, C=UK");
        BigInteger serial = BigInteger.valueOf((long) (new Random()).nextInt(2147483647));
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer, serial,
            CertificateSigningRequest.NOT_BEFORE, CertificateSigningRequest.NOT_AFTER, subject, publicKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, this.createSubjectKeyIdentifier(publicKey));
        builder.addExtension(Extension.basicConstraints, false, new BasicConstraints(false));
        List<ASN1Encodable> subjectAlternativeNames = new ArrayList();
        String[] var13;
        int var14;
        int var15;
        String subjectAlternativeNameIp;
        if (subjectAlternativeNameDomains != null) {
            subjectAlternativeNames.add(new GeneralName(2, domain));
            var13 = subjectAlternativeNameDomains;
            var14 = subjectAlternativeNameDomains.length;

            for (var15 = 0; var15 < var14; ++var15) {
                subjectAlternativeNameIp = var13[var15];
                subjectAlternativeNames.add(new GeneralName(2, subjectAlternativeNameIp));
            }
        }

        if (subjectAlternativeNameIps != null) {
            var13 = subjectAlternativeNameIps;
            var14 = subjectAlternativeNameIps.length;

            for (var15 = 0; var15 < var14; ++var15) {
                subjectAlternativeNameIp = var13[var15];
                if (IPAddress.isValidIPv6WithNetmask(subjectAlternativeNameIp) || IPAddress
                    .isValidIPv6(subjectAlternativeNameIp) || IPAddress.isValidIPv4WithNetmask(subjectAlternativeNameIp)
                    || IPAddress.isValidIPv4(subjectAlternativeNameIp)) {
                    subjectAlternativeNames.add(new GeneralName(7, subjectAlternativeNameIp));
                }
            }
        }

        if (subjectAlternativeNames.size() > 0) {
            DERSequence subjectAlternativeNamesExtension = new DERSequence(
                (ASN1Encodable[]) subjectAlternativeNames.toArray(new ASN1Encodable[0]));
            builder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension);
        }

        X509Certificate signedX509Certificate = this.signCertificate(builder, certificateAuthorityPrivateKey);
        signedX509Certificate.checkValidity(new Date());
        signedX509Certificate.verify(certificateAuthorityPublicKey);
        return signedX509Certificate;
    }

    private X509Certificate signCertificate(X509v3CertificateBuilder certificateBuilder, PrivateKey privateKey)
        throws OperatorCreationException, CertificateException {
        if (privateKey instanceof RSAPrivateKey) {
            ContentSigner signer = (new JcaContentSignerBuilder("SHA256WithRSAEncryption")).setProvider("BC")
                .build(privateKey);
            return (new JcaX509CertificateConverter()).setProvider("BC").getCertificate(certificateBuilder.build(signer));
        } else {
            ContentSigner signer = (new JcaContentSignerBuilder("SHA256withECDSA")).setProvider("BC")
                .build(privateKey);
            return (new JcaX509CertificateConverter()).setProvider("BC").getCertificate(certificateBuilder.build(signer));
        }
    }

    private KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
        generator.initialize(keySize, new SecureRandom());
        return generator.generateKeyPair();
    }

    private SubjectKeyIdentifier createSubjectKeyIdentifier(Key key) throws IOException {
        ASN1InputStream is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()));

        SubjectKeyIdentifier var5;
        try {
            ASN1Sequence seq = (ASN1Sequence) is.readObject();
            SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
            var5 = (new BcX509ExtensionUtils()).createSubjectKeyIdentifier(info);
        } catch (Throwable var7) {
            try {
                is.close();
            } catch (Throwable var6) {
                var7.addSuppressed(var6);
            }

            throw var7;
        }

        is.close();
        return var5;
    }

    public boolean certificateNotYetCreated() {
        return eeIdentity == null;
    }
}
