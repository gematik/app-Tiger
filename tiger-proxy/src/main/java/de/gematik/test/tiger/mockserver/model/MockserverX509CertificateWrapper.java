/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.Accessors;

/*
 * @author jamesdbloom
 */
@Data
@Accessors(chain = true, fluent = true)
public class MockserverX509CertificateWrapper extends ObjectWithJsonToString {

  @JsonIgnore private java.security.cert.X509Certificate certificate;
  @JsonIgnore private byte[] certificateBytes;
  private String issuerDistinguishedName;
  private String subjectDistinguishedName;
  @JsonIgnore private String serialNumber;
  @JsonIgnore private String signatureAlgorithmName;

  public static MockserverX509CertificateWrapper with(
      java.security.cert.X509Certificate x509Certificate) {
    return new MockserverX509CertificateWrapper()
        .serialNumber(x509Certificate.getSerialNumber().toString())
        .issuerDistinguishedName(x509Certificate.getIssuerX500Principal().getName())
        .subjectDistinguishedName(x509Certificate.getSubjectX500Principal().getName())
        .signatureAlgorithmName(x509Certificate.getSigAlgName())
        .certificate(x509Certificate);
  }
}
