/*
 * Copyright (c) 2023 gematik GmbH
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

package de.gematik.test.tiger.common.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.exceptions.TigerFileSeparatorException;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.TigerPkiIdentityLoaderException;
import org.junit.jupiter.api.Test;

public class TigerPkiIdentityLoaderTest {

    @Test
    public void loadJks() {
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity("src/test/resources/egk_aut_keystore.jks;gematik"))
            .isNotNull();
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity("gematik;src/test/resources/egk_aut_keystore.jks"))
            .isNotNull();
        assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;jks"))
            .hasMessageContaining("file");
        assertThatThrownBy(
            () -> TigerPkiIdentityLoader.loadRbelPkiIdentity("src/test/resources/egk_aut_keystore.jks;jks"))
            .hasMessageContaining("password");
    }

    @Test
    public void loadJks_shouldContainChain() {
        assertThat(TigerPkiIdentityLoader
            .loadRbelPkiIdentity("src/test/resources/egk_aut_keystore.jks;gematik")
            .getCertificateChain())
            .hasSize(1);
        assertThat(TigerPkiIdentityLoader
            .loadRbelPkiIdentity("src/test/resources/keystore.bks;00")
            .getCertificateChain())
            .hasSize(0);
    }

    @Test
    public void loadBks() {
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity("src/test/resources/keystore.bks;00"))
            .isNotNull();
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity("00;src/test/resources/keystore.bks"))
            .isNotNull();
        assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;bks"))
            .hasMessageContaining("file");
    }

    @Test
    public void loadP12() {
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity("src/test/resources/customCa.p12;00"))
            .isNotNull();
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity("00;src/test/resources/customCa.p12"))
            .isNotNull();
        assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;p12"))
            .hasMessageContaining("file");
    }

    @Test
    public void loadCertPemAndPkcs8() {
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(
            "src/test/resources/rsa/cert.pem;src/test/resources/rsa/key.p8;pkcs8"))
            .isNotNull();
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(
            "pkcs8;src/test/resources/rsa/key.p8;src/test/resources/rsa/cert.pem"))
            .isNotNull();
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(
            "src/test/resources/rsa/key.pkcs8;src/test/resources/rsa/cert.pem"))
            .isNotNull();
    }

    @Test
    public void loadCertPemAndPkcs1() {
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(
            "src/test/resources/rsa_pkcs1/cert.pem;src/test/resources/rsa_pkcs1/key.pem;pkcs1"))
            .isNotNull();
        assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(
            "pkcs1;src/test/resources/rsa_pkcs1/key.pem;src/test/resources/rsa_pkcs1/cert.pem"))
            .isNotNull();
    }

    @Test
    public void loadChainFromSingleAliasP12() {
        final TigerPkiIdentity identity = TigerPkiIdentityLoader
            .loadRbelPkiIdentity("src/test/resources/hera.p12;00");

        assertThat(identity.getCertificateChain())
            .hasSize(1);
        assertThat(identity.getCertificateChain().get(0))
            .isNotEqualTo(identity.getCertificate());
        assertThat(identity.getCertificateChain().get(0).getSubjectDN())
            .isEqualTo(identity.getCertificateChain().get(0).getIssuerDN());
        assertThat(identity.getCertificateChain().get(0).getSubjectDN())
            .isEqualTo(identity.getCertificate().getIssuerDN());
    }

    @Test
    public void useBackslashesAsFileSeparator() {
        assertThatThrownBy(() -> TigerPkiIdentityLoader
            .loadRbelPkiIdentity("D:\\tiger\\tiger-common\\src\\test\\resources\\customCa.p12"))
            .isInstanceOf(TigerFileSeparatorException.class)
            .hasMessageContaining("Please use forward slash (/) as a file separator");
    }

    @Test
    public void loadIncorrectPath() {
        assertThatThrownBy(() -> TigerPkiIdentityLoader
            .loadRbelPkiIdentity("src/test/fdfdxsss/herffssa.p1sddef2;00"))
            .isInstanceOf(TigerPkiIdentityLoaderException.class)
            .hasMessageContaining("Unable to determine store-type for input");
    }
}
