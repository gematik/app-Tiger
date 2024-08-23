/*
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

package de.gematik.test.tiger.mockserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/*
 * @author jamesdbloom
 */
@Data
@EqualsAndHashCode(callSuper = false)
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
