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
package de.gematik.test.tiger.common.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import org.apache.commons.lang3.NotImplementedException;
import org.bouncycastle.openssl.PEMException;
import org.junit.jupiter.api.Test;

class TestKeyMgr {

  final String prkPkcs8 =
      "-----BEGIN PRIVATE KEY-----\n"
          + "MIGIAgEAMBQGByqGSM49AgEGCSskAwMCCAEBBwRtMGsCAQEEIAeOzpSQT8a/mQDM\n"
          + "7Uxa9NzU++vFhbIFS2Nsw/djM73uoUQDQgAEIfr+3Iuh71R3mVooqXlPhjVd8wXx\n"
          + "9Yr8iPh+kcZkNTongD49z2cL0wXzuSP5Fb/hGTidhpw1ZYKMib1CIjH59A==\n"
          + "-----END PRIVATE KEY-----\n";

  final String puKPem =
      "-----BEGIN PUBLIC KEY-----\n"
          + "MFowFAYHKoZIzj0CAQYJKyQDAwIIAQEHA0IABEC6Sfy6RcfusiYbG+Drx8FNZIS5\n"
          + "74ojsGDr5n+XJSu8mHuknfNkoMmSbytt4br0YGihOixcmBKy80UfSLdXGe4=\n"
          + "-----END PUBLIC KEY-----\n";

  final String prKPem =
      "-----BEGIN PRIVATE KEY-----\n"
          + "MIGIAgEAMBQGByqGSM49AgEGCSskAwMCCAEBBwRtMGsCAQEEIBkuVz2ONqxyu3K+\n"
          + "VcDQOf02UoomXly1enoxElV101KQoUQDQgAEQLpJ/LpFx+6yJhsb4OvHwU1khLnv\n"
          + "iiOwYOvmf5clK7yYe6Sd82SgyZJvK23huvRgaKE6LFyYErLzRR9It1cZ7g==\n"
          + "-----END PRIVATE KEY-----";

  final String certPem =
      "-----BEGIN CERTIFICATE-----\n"
          + "MIICsTCCAligAwIBAgIHAbssqQhqOzAKBggqhkjOPQQDAjCBhDELMAkGA1UEBhMC\n"
          + "REUxHzAdBgNVBAoMFmdlbWF0aWsgR21iSCBOT1QtVkFMSUQxMjAwBgNVBAsMKUtv\n"
          + "bXBvbmVudGVuLUNBIGRlciBUZWxlbWF0aWtpbmZyYXN0cnVrdHVyMSAwHgYDVQQD\n"
          + "DBdHRU0uS09NUC1DQTEwIFRFU1QtT05MWTAeFw0yMTAxMTUwMDAwMDBaFw0yNjAx\n"
          + "MTUyMzU5NTlaMEkxCzAJBgNVBAYTAkRFMSYwJAYDVQQKDB1nZW1hdGlrIFRFU1Qt\n"
          + "T05MWSAtIE5PVC1WQUxJRDESMBAGA1UEAwwJSURQIFNpZyAzMFowFAYHKoZIzj0C\n"
          + "AQYJKyQDAwIIAQEHA0IABIYZnwiGAn5QYOx43Z8MwaZLD3r/bz6BTcQO5pbeum6q\n"
          + "QzYD5dDCcriw/VNPPZCQzXQPg4StWyy5OOq9TogBEmOjge0wgeowDgYDVR0PAQH/\n"
          + "BAQDAgeAMC0GBSskCAMDBCQwIjAgMB4wHDAaMAwMCklEUC1EaWVuc3QwCgYIKoIU\n"
          + "AEwEggQwIQYDVR0gBBowGDAKBggqghQATASBSzAKBggqghQATASBIzAfBgNVHSME\n"
          + "GDAWgBQo8Pjmqch3zENF25qu1zqDrA4PqDA4BggrBgEFBQcBAQQsMCowKAYIKwYB\n"
          + "BQUHMAGGHGh0dHA6Ly9laGNhLmdlbWF0aWsuZGUvb2NzcC8wHQYDVR0OBBYEFC94\n"
          + "M9LgW44lNgoAbkPaomnLjS8/MAwGA1UdEwEB/wQCMAAwCgYIKoZIzj0EAwIDRwAw\n"
          + "RAIgCg4yZDWmyBirgxzawz/S8DJnRFKtYU/YGNlRc7+kBHcCIBuzba3GspqSmoP1\n"
          + "VwMeNNKNaLsgV8vMbDJb30aqaiX1\n"
          + "-----END CERTIFICATE-----";

  @Test
  void testCertOK() {
    Certificate k = KeyMgr.readCertificateFromPem(certPem);
    assertThat(k).isInstanceOf(Certificate.class);
  }

  @Test
  void testCertInvalid() {
    assertThatThrownBy(
            () -> {
              KeyMgr.readCertificateFromPem("This is no PEM Cert");
            })
        .hasMessage("parsing issue: malformed PEM data: no header found");
  }

  @Test
  void testPrKeyOK() {
    Key k = KeyMgr.readPrivateKeyFromPem(prKPem);
    assertThat(k).isInstanceOf(PrivateKey.class);
  }

  @Test
  void testPrKInvalid() {
    assertThatThrownBy(
            () -> {
              KeyMgr.readPrivateKeyFromPem("This is no PEM Cert");
            })
        .isInstanceOf(PEMException.class);
  }

  @Test
  void testPubKeyNotImplementedPrKOK() {
    assertThatThrownBy(
            () -> {
              KeyMgr.readKeyFromPem(puKPem);
            })
        .isInstanceOf(NotImplementedException.class);
    Key k = KeyMgr.readKeyFromPem(prKPem);
    assertThat(k).isInstanceOf(PrivateKey.class);
  }

  @Test
  void readKeyPairFromEcdsaPkcs8Pem() {
    assertThat(KeyMgr.readEcdsaKeypairFromPkcs8Pem(prKPem.getBytes(StandardCharsets.UTF_8)))
        .isNotNull();
  }
}
