/*
 * Copyright (c) 2024 gematik GmbH
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

import static lombok.AccessLevel.PRIVATE;

import de.gematik.test.tiger.common.pki.TigerConfigurationPkiIdentity;
import java.security.cert.X509Certificate;
import java.util.Date;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.ocsp.jcajce.JcaRespID;
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

    RespID respID = new JcaRespID(certificate.getSubjectX500Principal());

    BasicOCSPRespBuilder basicRespBuilder = new BasicOCSPRespBuilder(respID);

    CertificateID certID =
      new CertificateID(
        new BcDigestCalculatorProvider().get(CertificateID.HASH_SHA1),
        new JcaX509CertificateHolder(certificate),
        certificate.getSerialNumber());

    basicRespBuilder.addResponse(certID, CertificateStatus.GOOD);

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
    BasicOCSPResp basicOcspResp = basicRespGen.build(contentSigner, null, new Date());

    var ocspResponseGenerator = new OCSPRespBuilder();
    var ocspResponse = ocspResponseGenerator.build(OCSPRespBuilder.SUCCESSFUL, basicOcspResp);

    return ocspResponse.getEncoded();
  }
}
