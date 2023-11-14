/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.tls;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import de.gematik.test.tiger.proxy.exceptions.TigerProxyStartupException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Random;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TlsCertificateGenerator {

  public static TigerPkiIdentity generateNewCaCertificate() {
    try {
      return generateNewCaCertificateUnsafe();
    } catch (GeneralSecurityException | IOException | OperatorCreationException e) {
      throw new TigerProxyStartupException("Error while generating CA certificate", e);
    }
  }

  private static TigerPkiIdentity generateNewCaCertificateUnsafe()
      throws GeneralSecurityException, IOException, OperatorCreationException {
    KeyPair keyPair = generateRsaKeyPair(2048);
    X500Name subject = new X500Name("CN=Tiger-Proxy, O=Gematik, L=Berlin, ST=Berlin, C=DE");

    BigInteger serial = BigInteger.valueOf(new Random().nextInt(Integer.MAX_VALUE)); // NOSONAR

    X509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            subject,
            serial,
            Date.from(ZonedDateTime.now().minusYears(1).toInstant()),
            Date.from(ZonedDateTime.now().plusYears(10).toInstant()),
            subject,
            keyPair.getPublic());
    builder.addExtension(
        Extension.subjectKeyIdentifier, false, createNewSubjectKeyIdentifier(keyPair.getPublic()));
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(100));
    builder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.cRLSign | KeyUsage.keyCertSign | KeyUsage.digitalSignature));

    final X509Certificate certificate = signTheCertificate(builder, keyPair.getPrivate());
    return new TigerPkiIdentity(certificate, keyPair.getPrivate());
  }

  private static X509Certificate signTheCertificate(
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

  private static KeyPair generateRsaKeyPair(int keySize) throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
    generator.initialize(keySize, new SecureRandom());
    return generator.generateKeyPair();
  }

  private static SubjectKeyIdentifier createNewSubjectKeyIdentifier(Key key) throws IOException {
    try (ASN1InputStream is = new ASN1InputStream(new ByteArrayInputStream(key.getEncoded()))) {
      ASN1Sequence seq = (ASN1Sequence) is.readObject();
      SubjectPublicKeyInfo info = SubjectPublicKeyInfo.getInstance(seq);
      return new BcX509ExtensionUtils().createSubjectKeyIdentifier(info);
    }
  }
}
