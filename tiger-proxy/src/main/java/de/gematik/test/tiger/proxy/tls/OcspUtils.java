/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy.tls;

import static lombok.AccessLevel.PRIVATE;

import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import java.security.cert.X509Certificate;
import java.util.Date;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

@Slf4j
@NoArgsConstructor(access = PRIVATE)
public class OcspUtils {
  @SneakyThrows
  public static byte[] buildOcspResponse(
      X509Certificate certificate, TigerConfigurationPkiIdentity ocspSignerIdentity) {
    log.info("Building OCSP response...");

    CertificateID certID =
        new CertificateID(
            new BcDigestCalculatorProvider().get(CertificateID.HASH_SHA1),
            new JcaX509CertificateHolder(certificate),
            certificate.getSerialNumber());

    final String signerDigestAlgorithm = ocspSignerIdentity.getCertificate().getSigAlgName();
    ContentSigner contentSigner =
        new JcaContentSignerBuilder(signerDigestAlgorithm)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(ocspSignerIdentity.getPrivateKey());

    BasicOCSPRespBuilder basicRespGen =
        new BasicOCSPRespBuilder(
            SubjectPublicKeyInfo.getInstance(
                ocspSignerIdentity.getCertificate().getPublicKey().getEncoded()),
            new JcaDigestCalculatorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build()
                .get(RespID.HASH_SHA1));
    basicRespGen.addResponse(certID, CertificateStatus.GOOD);
    BasicOCSPResp basicOcspResp =
        basicRespGen.build(
            contentSigner,
            new X509CertificateHolder[] {
              new X509CertificateHolder(ocspSignerIdentity.getCertificate().getEncoded())
            },
            new Date());

    var ocspResponseGenerator = new OCSPRespBuilder();
    var ocspResponse = ocspResponseGenerator.build(OCSPRespBuilder.SUCCESSFUL, basicOcspResp);

    return ocspResponse.getEncoded();
  }
}
