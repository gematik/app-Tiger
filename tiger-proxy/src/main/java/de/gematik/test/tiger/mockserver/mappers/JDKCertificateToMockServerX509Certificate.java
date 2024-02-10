/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.mappers;


import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.X509Certificate;
import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/*
 * @author jamesdbloom
 */
@Slf4j
public class JDKCertificateToMockServerX509Certificate {

  public HttpRequest setClientCertificates(
      HttpRequest httpRequest, Certificate[] clientCertificates) {
    if (clientCertificates != null) {
      List<X509Certificate> x509Certificates =
          Arrays.stream(clientCertificates)
              .flatMap(
                  certificate -> {
                    try {
                      java.security.cert.X509Certificate x509Certificate =
                          (java.security.cert.X509Certificate)
                              CertificateFactory.getInstance("X.509")
                                  .generateCertificate(
                                      new ByteArrayInputStream(certificate.getEncoded()));
                      return Stream.of(
                          new X509Certificate()
                              .withSerialNumber(x509Certificate.getSerialNumber().toString())
                              .withIssuerDistinguishedName(
                                  x509Certificate.getIssuerX500Principal().getName())
                              .withSubjectDistinguishedName(
                                  x509Certificate.getSubjectX500Principal().getName())
                              .withSignatureAlgorithmName(x509Certificate.getSigAlgName())
                              .withCertificate(certificate));
                    } catch (Exception e) {
                      log.info("exception decoding client certificate", e);
                    }
                    return Stream.empty();
                  })
              .toList();
      if (!x509Certificates.isEmpty()) {
        httpRequest.setClientCertificateChain(x509Certificates);
      }
    }
    return httpRequest;
  }
}
