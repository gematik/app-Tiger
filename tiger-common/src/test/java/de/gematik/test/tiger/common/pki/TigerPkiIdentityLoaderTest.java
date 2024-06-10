/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.common.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.TigerPkiIdentityLoaderException;
import java.io.File;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TigerPkiIdentityLoaderTest {

  @AfterEach
  void afterAll() {
    TigerGlobalConfiguration.reset();
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
            src/test/resources/egk_aut_keystore.jks;gematik,    gematik;src/test/resources/egk_aut_keystore.jks,     src/test/resources/egk_aut_keystore.jks;jks
            src\\test\\resources\\egk_aut_keystore.jks;gematik, gematik;src\\test\\resources\\egk_aut_keystore.jks,  src\\test\\resources\\egk_aut_keystore.jks;jks
            """)
  void loadJks(String pathKey, String keyPath, String pathStoreType) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(pathKey)).isNotNull();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyPath)).isNotNull();
    assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;jks"))
        .hasMessageContaining("file");
    assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity(pathStoreType))
        .hasMessageContaining("password");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
            src/test/resources/egk_aut_keystore.jks;gematik,       src/test/resources/keystore.bks;00
            src\\test\\resources\\egk_aut_keystore.jks;gematik,    src\\test\\resources\\keystore.bks;00
            """)
  void loadJks_shouldContainChain(String keyStoreJks, String keyStoreBks) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyStoreJks).getCertificateChain())
        .hasSize(1);
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyStoreBks).getCertificateChain())
        .isEmpty();
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
            src/test/resources/keystore.bks;00,       00;src/test/resources/keystore.bks
            src\\test\\resources\\keystore.bks;00,       00;src\\test\\resources\\keystore.bks
            """)
  void loadBks(String pathKey, String keyPath) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(pathKey)).isNotNull();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyPath)).isNotNull();
    assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;bks"))
        .hasMessageContaining("file");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
            src/test/resources/customCa.p12;00,      00;src/test/resources/customCa.p12
            src\\test\\resources\\customCa.p12;00,      00;src\\test\\resources\\customCa.p12
            """)
  void loadP12(String pathKey, String keyPath) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(pathKey)).isNotNull();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyPath)).isNotNull();
    assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;p12"))
        .hasMessageContaining("file");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
            src/test/resources/rsa/cert.pem;src/test/resources/rsa/key.p8;pkcs8,      pkcs8;src/test/resources/rsa/key.p8;src/test/resources/rsa/cert.pem, src/test/resources/rsa/key.pkcs8;src/test/resources/rsa/cert.pem
            src\\test\\resources\\rsa\\cert.pem;src\\test\\resources\\rsa\\key.p8;pkcs8,      pkcs8;src\\test\\resources\\rsa\\key.p8;src\\test\\resources\\rsa\\cert.pem, src\\test\\resources\\rsa\\key.pkcs8;src\\test\\resources\\rsa\\cert.pem
            """)
  void loadCertPemAndPkcs8(String pemP8Key, String keyp8Pem, String pkcs8Pem) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(pemP8Key)).isNotNull();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyp8Pem)).isNotNull();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(pkcs8Pem)).isNotNull();
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
            src/test/resources/rsa_pkcs1/cert.pem;src/test/resources/rsa_pkcs1/key.pem;pkcs1,      pkcs1;src/test/resources/rsa_pkcs1/key.pem;src/test/resources/rsa_pkcs1/cert.pem
            src\\test\\resources\\rsa_pkcs1\\cert.pem;src\\test\\resources\\rsa_pkcs1\\key.pem;pkcs1,      pkcs1;src\\test\\resources\\rsa_pkcs1\\key.pem;src\\test\\resources\\rsa_pkcs1\\cert.pem
            """)
  void loadCertPemAndPkcs1(String certpemKeypemKey, String keyKeypemCertpem) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(certpemKeypemKey)).isNotNull();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(keyKeypemCertpem)).isNotNull();
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
           src/test/resources/hera.p12;00
           src\\test\\resources\\hera.p12;00
            """)
  void loadChainFromSingleAliasP12(String heraP12) {
    final TigerPkiIdentity identity = TigerPkiIdentityLoader.loadRbelPkiIdentity(heraP12);

    assertThat(identity.getCertificateChain()).hasSize(2);
    assertThat(identity.getCertificateChain().get(0)).isNotEqualTo(identity.getCertificate());
    assertThat(identity.getCertificateChain().get(0).getSubjectX500Principal())
        .isEqualTo(identity.getCertificateChain().get(0).getIssuerX500Principal());
    assertThat(identity.getCertificateChain().get(0).getSubjectX500Principal())
        .isEqualTo(identity.getCertificate().getIssuerX500Principal());
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
           src/test/resources/customCa.p12
           src/test/resources/nonDefaultPw.p12
           src/test/resources/hera.p12
            """)
  void loadWithDefaultKeystorePasswords(String path) {
    TigerGlobalConfiguration.initialize();
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentityWithGuessedPassword(new File(path)))
        .isNotNull();
  }

  @Test
  void additionalKeystorePasswords() {
    TigerGlobalConfiguration.readFromYaml(
        """
      tiger:
        lib:
          additionalKeyStorePasswords: ["foo", "baz", "bar"]
      """);
    List<String> list = TigerPkiIdentityLoader.getAllKeystorePasswords();
    assertThat(list)
        .containsAll(List.of("00", "123456", "gematik", "changeit", "foo", "baz", "bar"));
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
           src/test/fdfdxsss/herffssa.p1sddef2;00
           src\\test\\fdfdxsss\\herffssa.p1sddef2;00
            """)
  void loadIncorrectPath(String incorrectPath) {
    assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity(incorrectPath))
        .isInstanceOf(TigerPkiIdentityLoaderException.class)
        .hasMessage("Unable to determine store-type for input '%s'!".formatted(incorrectPath));
  }
}
