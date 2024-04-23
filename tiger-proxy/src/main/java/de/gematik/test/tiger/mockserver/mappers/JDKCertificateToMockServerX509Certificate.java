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

package de.gematik.test.tiger.mockserver.mappers;

import de.gematik.test.tiger.mockserver.model.HttpRequest;
import de.gematik.test.tiger.mockserver.model.MockserverX509CertificateWrapper;
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
      List<MockserverX509CertificateWrapper> x509Certificates =
          Arrays.stream(clientCertificates)
              .flatMap(
                  certificate -> {
                    try {
                      java.security.cert.X509Certificate x509Certificate =
                          (java.security.cert.X509Certificate)
                              CertificateFactory.getInstance("X.509")
                                  .generateCertificate(
                                      new ByteArrayInputStream(certificate.getEncoded()));
                      return Stream.of(MockserverX509CertificateWrapper.with(x509Certificate));
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
