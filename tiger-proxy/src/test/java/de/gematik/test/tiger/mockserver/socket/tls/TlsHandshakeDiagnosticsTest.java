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
package de.gematik.test.tiger.mockserver.socket.tls;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.pki.TigerPkiIdentity;
import java.util.List;
import javax.net.ssl.SSLException;
import org.bouncycastle.tls.CipherSuite;
import org.junit.jupiter.api.Test;

class TlsHandshakeDiagnosticsTest {

  @Test
  void shouldExplainWhyNoCipherSuiteCanBeSelected() {
    TigerPkiIdentity eccIdentity =
        new TigerPkiIdentity("src/test/resources/eccServerCertificate.p12;00");

    assertThat(
            TlsHandshakeDiagnostics.cipherSuiteMismatch(
                List.of(
                    "TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"),
                List.of(
                    "TLS_DHE_RSA_WITH_AES_256_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"),
                eccIdentity))
        .hasValueSatisfying(
            message ->
                assertThat(message)
                    .contains(
                        "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                        "yes     | no      | disallowed by server-suites",
                        "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
                        "no      | yes     | disallowed by client-hello",
                        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                        "yes     | yes     | incompatible with server-certificate"));
  }

  @Test
  void shouldNotReportMismatchWhenMutuallyUsableSuiteExists() {
    TigerPkiIdentity rsaIdentity = new TigerPkiIdentity("src/test/resources/rsa.p12;00");

    assertThat(
            TlsHandshakeDiagnostics.cipherSuiteMismatch(
                List.of("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"),
                List.of("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"),
                rsaIdentity))
        .isEmpty();
  }

  @Test
  void shouldResolveCipherSuiteNamesFromClientHelloIds() {
    assertThat(
            TlsHandshakeDiagnostics.cipherSuiteNames(
                new int[] {CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA, 0x1234}))
        .containsExactly("TLS_DHE_RSA_WITH_AES_128_CBC_SHA", "UNKNOWN_CIPHER_SUITE_0x1234");
  }

  @Test
  void shouldDescribeRejectedServerCertificateAndSuggestedRemedies() {
    TigerPkiIdentity rsaIdentity = new TigerPkiIdentity("src/test/resources/rsa.p12;00");

    assertThat(TlsHandshakeDiagnostics.certificateUnknown(rsaIdentity))
        .contains(
            rsaIdentity.getCertificate().getSubjectX500Principal().toString(),
            rsaIdentity.getCertificate().getIssuerX500Principal().toString(),
            "SHA-256 fingerprint:",
            "client's truststore",
            "Configure the server to present a certificate trusted by the client");
  }

  @Test
  void shouldInspectNestedExceptionMessages() {
    Throwable throwable =
        new SSLException("handshake failed", new SSLException("alert: certificate_unknown"));

    assertThat(TlsHandshakeDiagnostics.causeMessageContains(throwable, "certificate_unknown"))
        .isTrue();
  }
}
