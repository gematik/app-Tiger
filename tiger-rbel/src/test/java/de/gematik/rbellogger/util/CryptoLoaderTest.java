/*
 * Copyright (c) 2022 gematik GmbH
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

package de.gematik.rbellogger.util;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class CryptoLoaderTest {

    @Test
    @DisplayName("Load ECC Identity from PEM and PKCS8 files")
    public void loadEccIdentityFromp8AndCert() throws IOException {
        final RbelPkiIdentity pkiIdentity = CryptoLoader.getIdentityFromPemAndPkcs8(
            FileUtils.readFileToByteArray(new File("src/test/resources/ecc/cert.pem")),
            FileUtils.readFileToByteArray(new File("src/test/resources/ecc/key.p8"))
        );

        assertThat(pkiIdentity.getCertificate().getType())
            .isEqualTo("X.509");
        assertThat(pkiIdentity.getCertificate().getPublicKey().getAlgorithm())
            .isEqualTo("EC");
        assertThat(pkiIdentity.getPrivateKey().getAlgorithm())
            .isEqualTo("EC");
    }

    @Test
    @DisplayName("Load RSA Identity from PEM and PKCS8 files")
    public void loadRsaIdentityFromP8AndCert() throws IOException {
        final RbelPkiIdentity pkiIdentity = CryptoLoader.getIdentityFromPemAndPkcs8(
            FileUtils.readFileToByteArray(new File("src/test/resources/rsa/cert.pem")),
            FileUtils.readFileToByteArray(new File("src/test/resources/rsa/key.p8"))
        );

        assertThat(pkiIdentity.getCertificate().getType())
            .isEqualTo("X.509");
        assertThat(pkiIdentity.getCertificate().getPublicKey().getAlgorithm())
            .isEqualTo("RSA");
        assertThat(pkiIdentity.getPrivateKey().getAlgorithm())
            .isEqualTo("RSA");
    }

    @Test
    @DisplayName("Load RSA Identity from PEM and PKCS1 files")
    public void loadRsaIdentityFromP1AndCert() throws IOException {
        final RbelPkiIdentity pkiIdentity = CryptoLoader.getIdentityFromPemAndPkcs1(
            FileUtils.readFileToByteArray(new File("src/test/resources/rsa_pkcs1/cert.pem")),
            FileUtils.readFileToByteArray(new File("src/test/resources/rsa_pkcs1/key.pem"))
        );

        assertThat(pkiIdentity.getCertificate().getType())
            .isEqualTo("X.509");
        assertThat(pkiIdentity.getCertificate().getPublicKey().getAlgorithm())
            .isEqualTo("RSA");
        assertThat(pkiIdentity.getPrivateKey().getAlgorithm())
            .isEqualTo("RSA");
    }

    @Test
    @DisplayName("Load ECC Identity from P12 file")
    public void loadEccIdentityFromP12() throws IOException {
        final RbelPkiIdentity pkiIdentity = CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/idpEnc.p12")),
            "00"
        );

        assertThat(pkiIdentity.getCertificate().getType())
            .isEqualTo("X.509");
        assertThat(pkiIdentity.getCertificate().getPublicKey().getAlgorithm())
            .isEqualTo("EC");
        assertThat(pkiIdentity.getPrivateKey().getAlgorithm())
            .isEqualTo("EC");
    }

    @Test
    @DisplayName("Load RSA Identity from P12 file")
    public void loadRsaIdentityFromP12() throws IOException {
        final RbelPkiIdentity pkiIdentity = CryptoLoader.getIdentityFromP12(
            FileUtils.readFileToByteArray(new File("src/test/resources/rsa.p12")),
            "00"
        );

        assertThat(pkiIdentity.getCertificate().getType())
            .isEqualTo("X.509");
        assertThat(pkiIdentity.getCertificate().getPublicKey().getAlgorithm())
            .isEqualTo("RSA");
        assertThat(pkiIdentity.getPrivateKey().getAlgorithm())
            .isEqualTo("RSA");
    }
}
