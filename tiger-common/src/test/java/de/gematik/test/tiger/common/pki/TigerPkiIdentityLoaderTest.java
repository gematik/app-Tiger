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

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.pki.TigerPkiIdentityLoader.TigerPkiIdentityLoaderException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TigerPkiIdentityLoaderTest {

  @AfterEach
  void afterAll() {
    TigerGlobalConfiguration.reset();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "src/test/resources/egk_aut_keystore.jks;gematik",
        "src\\test\\resources\\egk_aut_keystore.jks;gematik",
        "gematik;src/test/resources/egk_aut_keystore.jks",
        "gematik;src\\test\\resources\\egk_aut_keystore.jks"
      })
  void loadJksWithDifferentPaths(String path) {
    assertThat(TigerPkiIdentityLoader.loadRbelPkiIdentity(path)).isNotNull();
  }

  @Test
  void testExceptionIsThrownWithoutProperFilename() {
    assertThatThrownBy(() -> TigerPkiIdentityLoader.loadRbelPkiIdentity("foo;bar;jks"))
        .hasMessageContaining("file");
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

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        myKey:
          filename: src/test/resources/egk_aut_keystore.jks
          password: gematik
        """,
        """
        replace.with: src/test/resources/egk_aut_keystore.jks
        somePw: gematik
        myKey:
          filename: ${base.replace.with}
          password: ${base.somePw}
        """,
        """
        myKey:
          filename: "src/test/resources/someP12File.bin"
          storeType: p12
        """,
        """
        myKey:
          filename: "src/test/resources/pwWithSemicolon.p12"
          password: "Semi;colon"
        """,
        """
        myKey: src/test/resources/egk_aut_keystore.jks
        """
      })
  void initializeWithDetailDescriptionOrWithout(String yamlFragment) {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml(yamlFragment, "base");
    final Optional<NestedKeyClass> blub =
        TigerGlobalConfiguration.instantiateConfigurationBean(NestedKeyClass.class, "base");
    assertThat(blub).get().extracting("myKey").extracting("certificate").isNotNull();
    assertThat(blub).get().extracting("myKey").extracting("privateKey").isNotNull();
  }

  @Test
  void readCompactForm_compactFormShouldBeUsedForDeserialization() {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml("myKey: src/test/resources/egk_aut_keystore.jks;gematik");
    val key =
        TigerGlobalConfiguration.instantiateConfigurationBean(
                TigerConfigurationPkiIdentity.class, "myKey")
            .get();
    TigerGlobalConfiguration.putValue("another.key", key);
    final Map<String, String> map = TigerGlobalConfiguration.readMap("another");
    assertThat(map)
        .containsExactly(Pair.of("key", "src/test/resources/egk_aut_keystore.jks;gematik;JKS"));
  }

  @Test
  void readObjectForm_objectFormShouldBeUsedForDeserialization() {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml(
        """
        myKey:
          filename: src/test/resources/egk_aut_keystore.jks
          password: gematik
        """);
    val key =
        TigerGlobalConfiguration.instantiateConfigurationBean(
                TigerConfigurationPkiIdentity.class, "myKey")
            .get();
    TigerGlobalConfiguration.putValue("another.key", key);
    final Map<String, String> map = TigerGlobalConfiguration.readMap("another");
    assertThat(map)
        .containsExactly(
            Pair.of("key.filenames.0", "src/test/resources/egk_aut_keystore.jks"),
            Pair.of("key.password", "gematik"));
  }

  @Data
  public static class NestedKeyClass {
    private final TigerConfigurationPkiIdentity myKey;
  }
}
