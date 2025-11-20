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
package de.gematik.rbellogger.converter;

import de.gematik.rbellogger.RbelConverter;
import de.gematik.rbellogger.RbelLogger;
import de.gematik.rbellogger.configuration.RbelConfiguration;
import de.gematik.rbellogger.facets.pki.ocsp.RbelOcspRequestFacet;
import de.gematik.rbellogger.renderer.RbelHtmlRenderer;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@Slf4j
class RbelOcspRequestConverterTest {

  private static OCSPReq request;
  private static RbelConverter converter;

  @SneakyThrows
  @BeforeAll
  public static void setUp(TestInfo testInfo) {
    log.info("Setting up test '{}'", testInfo.getDisplayName());
    converter =
        RbelLogger.build(
                RbelConfiguration.builder().activateRbelParsingFor(List.of("OCSP")).build())
            .getRbelConverter();

    request = generateOcspRequest();
  }

  private static OCSPReq generateOcspRequest() throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair keyPair = keyGen.generateKeyPair();
    X509Certificate issuerCert = generateSelfSignedCertificate(keyPair, "CN=TestIssuer");
    X509CertificateHolder issuerHolder = new X509CertificateHolder(issuerCert.getEncoded());

    DigestCalculator digestCalculator =
        new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1);
    BigInteger serialNumber = BigInteger.valueOf(123456789L);
    CertificateID certId = new CertificateID(digestCalculator, issuerHolder, serialNumber);
    OCSPReqBuilder builder = new OCSPReqBuilder();
    builder.addRequest(certId);

    GeneralName requestorName = new GeneralName(issuerHolder.getSubject());
    builder.setRequestorName(requestorName);

    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    return builder.build(signer, new X509CertificateHolder[] {issuerHolder});
  }

  // Helper to generate a minimal self-signed X509Certificate using BouncyCastle
  private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, String dn)
      throws Exception {
    long now = System.currentTimeMillis();
    Date startDate = new Date(now);
    Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000);
    X500Name x500Name = new X500Name(dn);
    BigInteger certSerialNumber = BigInteger.valueOf(now);
    ContentSigner contentSigner =
        new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    SubjectPublicKeyInfo subjPubKeyInfo =
        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(
            x500Name, certSerialNumber, startDate, endDate, x500Name, subjPubKeyInfo);
    return new JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner));
  }

  @Test
  @SneakyThrows
  void shouldParseOcspRequestAndExtractFacetFromBinary() {
    var ocspRequestBinary = converter.convertElement(request.getEncoded(), null);
    log.debug(ocspRequestBinary.printTreeStructure());

    Assertions.assertThat(ocspRequestBinary).isNotNull();
    Assertions.assertThat(ocspRequestBinary.hasFacet(RbelOcspRequestFacet.class)).isTrue();
    final RbelOcspRequestFacet facet = ocspRequestBinary.getFacetOrFail(RbelOcspRequestFacet.class);
    Assertions.assertThat(facet.getRequestorName()).isNotNull();
    Assertions.assertThat(facet.getSignatureAlgorithm()).isNotNull();
    Assertions.assertThat(facet.getRequests()).isNotNull();
  }

  @Test
  @SneakyThrows
  void shouldParseOcspRequestAndExtractFacetFromBase64() {
    String base64 = Base64.getEncoder().encodeToString(request.getEncoded());
    var ocspRequestBase64 = converter.convertElement(base64.getBytes(), null);
    log.debug(ocspRequestBase64.printTreeStructure());

    Assertions.assertThat(ocspRequestBase64).isNotNull();
    Assertions.assertThat(ocspRequestBase64.hasFacet(RbelOcspRequestFacet.class)).isTrue();
    final RbelOcspRequestFacet facet = ocspRequestBase64.getFacetOrFail(RbelOcspRequestFacet.class);
    Assertions.assertThat(facet.getRequestorName()).isNotNull();
    Assertions.assertThat(facet.getSignatureAlgorithm()).isNotNull();
    Assertions.assertThat(facet.getRequests()).isNotNull();
  }

  @Test
  @SneakyThrows
  void shouldRenderParsedOcspRequestFromBinary() {
    var ocspRequestBinary = converter.convertElement(request.getEncoded(), null);
    String html = RbelHtmlRenderer.render(List.of(ocspRequestBinary));

    Assertions.assertThat(html)
        .contains("OCSP Request")
        .contains("Requestor Name:")
        .contains("Signature Algorithm:")
        .contains("Single Request")
        // Check for single request details
        .contains("Serial Number:")
        .contains("Hash Algorithm:")
        .contains("Issuer Name Hash:")
        .contains("Issuer Key Hash:");
  }
}
