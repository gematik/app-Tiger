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

  @Test
  void aliasesOrPasswordsField_shouldBeIgnoredInConfiguration() {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml(
        """
        myKey:
          filename: src/test/resources/egk_aut_keystore.jks
          password: gematik
          aliasesOrPasswords: ["should", "be", "ignored"]
        """);

    val key =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "myKey");

    assertThat(key).isPresent();
    val identity = key.get();

    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();

    assertThat(identity.getFileLoadingInformation().getAliasesOrPasswords()).isEmpty();
  }

  @Test
  void aliasField_shouldWorkInConfiguration() {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml(
        """
        myKey:
          filename: src/test/resources/multikey.p12
          password: gematik
          alias: key2
        """);

    val key =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "myKey");

    assertThat(key).isPresent();
    val identity = key.get();

    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
    assertThat(identity.getFileLoadingInformation().getAlias()).isEqualTo("key2");
  }

  @Test
  void passwordField_shouldWorkInConfiguration() {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml(
        """
        myKey:
          filename: src/test/resources/egk_aut_keystore.jks
          password: gematik
        """);

    val key =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "myKey");

    assertThat(key).isPresent();
    val identity = key.get();

    // Verify that the identity loads successfully with password
    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
    assertThat(identity.getFileLoadingInformation().getPassword()).isEqualTo("gematik");
  }

  @Test
  void aliasAndPasswordFields_shouldWorkTogetherInConfiguration() {
    TigerGlobalConfiguration.initialize();
    TigerGlobalConfiguration.readFromYaml(
        """
        myKey:
          filename: src/test/resources/multikey.p12
          password: gematik
          alias: key2
        """);

    val key =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "myKey");

    assertThat(key).isPresent();
    val identity = key.get();

    // Verify that the identity loads successfully with both alias and password
    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
    assertThat(identity.getFileLoadingInformation().getPassword()).isEqualTo("gematik");
    assertThat(identity.getFileLoadingInformation().getAlias()).isEqualTo("key2");
  }

  @Test
  void compactFormat_shouldNotIncludeAliasesOrPasswordsWhenNotSet() {
    val identityInfo =
        TigerPkiIdentityInformation.builder()
            .filenames(List.of("test.jks"))
            .password("testpw")
            .alias("testalias")
            .storeType(TigerPkiIdentityLoader.StoreType.JKS)
            .build();

    String compactFormat = identityInfo.generateCompactFormat();

    // Should contain filename, password and store type, but not aliasesOrPasswords
    assertThat(compactFormat).isEqualTo("test.jks;testpw;testalias;JKS");
  }

  @Test
  void tigerPkiIdentityInformation_aliasesOrPasswordsNotSerializedToJson() throws Exception {
    val identityInfo =
        TigerPkiIdentityInformation.builder()
            .filenames(List.of("test.jks"))
            .password("testpw")
            .alias("testalias")
            .build();

    // Manually set aliasesOrPasswords (simulating internal usage)
    identityInfo.setAliasesOrPasswords(List.of("internal", "values"));

    // Serialize to JSON using the same ObjectMapper that would be used in configuration
    com.fasterxml.jackson.databind.ObjectMapper mapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    String json = mapper.writeValueAsString(identityInfo);

    // Verify that aliasesOrPasswords is not present in JSON
    assertThat(json).doesNotContain("aliasesOrPasswords");
    assertThat(json).contains("\"password\":\"testpw\"");
    assertThat(json).contains("\"alias\":\"testalias\"");

    // Verify that deserializing doesn't populate aliasesOrPasswords
    TigerPkiIdentityInformation deserialized =
        mapper.readValue(json, TigerPkiIdentityInformation.class);
    assertThat(deserialized.getAliasesOrPasswords()).isNullOrEmpty();
    assertThat(deserialized.getPassword()).isEqualTo("testpw");
    assertThat(deserialized.getAlias()).isEqualTo("testalias");
  }

  // Tests for compact format with alias support

  @ParameterizedTest
  @ValueSource(
      strings = {
        // Alias only formats
        "src/test/resources/egk_aut_keystore.jks;testAlias;JKS",
        "testAlias;src/test/resources/egk_aut_keystore.jks;JKS",
        "JKS;testAlias;src/test/resources/egk_aut_keystore.jks",
        // Password + Alias formats
        "src/test/resources/egk_aut_keystore.jks;gematik;testAlias;JKS",
        "gematik;src/test/resources/egk_aut_keystore.jks;testAlias;JKS",
        "testAlias;gematik;src/test/resources/egk_aut_keystore.jks;JKS",
        "src/test/resources/egk_aut_keystore.jks;testAlias;gematik;JKS",
        "JKS;gematik;testAlias;src/test/resources/egk_aut_keystore.jks"
      })
  void compactFormat_withAliasPermutations_shouldParseCorrectly(String compactFormat) {
    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames())
        .containsExactly("src/test/resources/egk_aut_keystore.jks");
    assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.JKS);

    if (compactFormat.contains("gematik")) {
      assertThat(parsedInfo.getAliasesOrPasswords())
          .containsExactlyInAnyOrder("gematik", "testAlias");
    } else {
      assertThat(parsedInfo.getAliasesOrPasswords()).containsExactly("testAlias");
    }
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
          # Format: compactString, expectedPassword, expectedAlias
          src/test/resources/multikey.p12;gematik;key1;PKCS12, gematik, key1
          gematik;src/test/resources/multikey.p12;key1;PKCS12, gematik, key1
          key1;gematik;src/test/resources/multikey.p12;PKCS12, gematik, key1
          src/test/resources/multikey.p12;key1;gematik;PKCS12, gematik, key1
          PKCS12;gematik;key1;src/test/resources/multikey.p12, gematik, key1
          # Alias only formats
          src/test/resources/multikey.p12;key2;PKCS12, , key2
          key2;src/test/resources/multikey.p12;PKCS12, , key2
          PKCS12;key2;src/test/resources/multikey.p12, , key2
          """)
  void compactFormat_multikeyKeystore_withPermutations_shouldLoadCorrectKey(
      String compactFormat, String expectedPassword, String expectedAlias) {

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames()).containsExactly("src/test/resources/multikey.p12");
    assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.PKCS12);

    if (expectedPassword != null && !expectedPassword.isEmpty()) {
      assertThat(parsedInfo.getAliasesOrPasswords())
          .containsExactlyInAnyOrder(expectedPassword, expectedAlias);
    } else {
      assertThat(parsedInfo.getAliasesOrPasswords()).containsExactly(expectedAlias);
    }

    // Try to load the identity with specific alias
    TigerPkiIdentity identity = TigerPkiIdentityLoader.loadIdentity(parsedInfo);

    // If successful, verify we got a valid identity
    assertThat(identity).isNotNull();
    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // Multiple aliases/passwords in different orders
        "src/test/resources/egk_aut_keystore.jks;password1;alias1;alias2;JKS",
        "password1;src/test/resources/egk_aut_keystore.jks;alias1;alias2;JKS",
        "alias1;password1;src/test/resources/egk_aut_keystore.jks;alias2;JKS",
        "JKS;password1;alias1;alias2;src/test/resources/egk_aut_keystore.jks",
        "alias2;alias1;password1;src/test/resources/egk_aut_keystore.jks;JKS"
      })
  void compactFormat_multipleAliasesOrPasswords_withPermutations_shouldHandleCorrectly(
      String compactFormat) {

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames())
        .containsExactly("src/test/resources/egk_aut_keystore.jks");
    assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.JKS);
    assertThat(parsedInfo.getAliasesOrPasswords())
        .containsExactlyInAnyOrder("password1", "alias1", "alias2");
  }

  @ParameterizedTest
  @CsvSource(
      textBlock =
          """
          # P12 keystore with different field orders
          src/test/resources/customCa.p12;00;testAlias;PKCS12
          00;src/test/resources/customCa.p12;testAlias;PKCS12
          testAlias;00;src/test/resources/customCa.p12;PKCS12
          PKCS12;00;testAlias;src/test/resources/customCa.p12
          # JKS keystore with different field orders
          src/test/resources/egk_aut_keystore.jks;gematik;myAlias;JKS
          gematik;src/test/resources/egk_aut_keystore.jks;myAlias;JKS
          myAlias;gematik;src/test/resources/egk_aut_keystore.jks;JKS
          JKS;gematik;myAlias;src/test/resources/egk_aut_keystore.jks
          """)
  void compactFormat_differentKeystoreTypes_withPermutations_shouldParseCorrectly(
      String compactFormat) {

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    boolean isP12 = compactFormat.contains(".p12");
    if (isP12) {
      assertThat(parsedInfo.getFilenames()).containsExactly("src/test/resources/customCa.p12");
      assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.PKCS12);
      assertThat(parsedInfo.getAliasesOrPasswords()).containsExactlyInAnyOrder("00", "testAlias");
    } else {
      assertThat(parsedInfo.getFilenames())
          .containsExactly("src/test/resources/egk_aut_keystore.jks");
      assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.JKS);
      assertThat(parsedInfo.getAliasesOrPasswords())
          .containsExactlyInAnyOrder("gematik", "myAlias");
    }
  }

  @Test
  void compactFormat_withAliasAndPassword_shouldLoadCorrectKey() {
    String compactFormat = "src/test/resources/multikey.p12;gematik;key2";

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames()).containsExactly("src/test/resources/multikey.p12");
    assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.PKCS12);
    assertThat(parsedInfo.getAliasesOrPasswords()).containsExactlyInAnyOrder("gematik", "key2");

    assertThat(TigerPkiIdentityLoader.loadIdentity(parsedInfo).getPrivateKey()).isNotNull();
  }

  @Test
  void compactFormat_aliasOnlyWithActualKeystore_shouldLoadSuccessfully() {
    String compactFormat = "src/test/resources/multikey.p12;key2";

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.isUseCompactFormat()).isTrue();
    assertThat(parsedInfo.getFilenames()).hasSize(1);
    assertThat(parsedInfo.getAliasesOrPasswords()).containsExactly("key2");

    assertThat(TigerPkiIdentityLoader.loadIdentity(parsedInfo).getPrivateKey()).isNotNull();
  }

  @Test
  void compactFormat_passwordAndAliasWithActualKeystore_shouldUseCorrectCredentials() {
    String compactFormat = "src/test/resources/multikey.p12;gematik;key2";

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames()).containsExactly("src/test/resources/multikey.p12");
    assertThat(parsedInfo.getAliasesOrPasswords()).containsExactlyInAnyOrder("gematik", "key2");

    TigerPkiIdentity identity = TigerPkiIdentityLoader.loadIdentity(parsedInfo);
    assertThat(identity).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
  }

  @Test
  void compactFormat_generateWithAliasAndPassword_shouldProduceCorrectFormat() {
    val identityInfo =
        TigerPkiIdentityInformation.builder()
            .filenames(List.of("test.jks"))
            .password("myPassword")
            .alias("myAlias")
            .storeType(TigerPkiIdentityLoader.StoreType.JKS)
            .aliasesOrPasswords(
                List.of("myPassword", "myAlias")) // Set internally for compact format
            .build();

    String compactFormat = identityInfo.generateCompactFormat();

    assertThat(compactFormat).contains("test.jks");
    assertThat(compactFormat).contains("myPassword");
    assertThat(compactFormat).contains("myAlias");
    assertThat(compactFormat).contains("JKS");
  }

  @ParameterizedTest
  @ValueSource(strings = {"key1", "key2", "key3"})
  void compactFormat_multikeyKeystore_withSpecificAlias_shouldLoadCorrectKey(String alias) {
    String keystorePath = "src/test/resources/multikey.p12";
    String password = "gematik";

    String compactFormat = keystorePath + ";" + password + ";" + alias + ";PKCS12";

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames()).containsExactly(keystorePath);
    assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.PKCS12);
    assertThat(parsedInfo.getAliasesOrPasswords()).containsExactlyInAnyOrder(password, alias);

    TigerPkiIdentity identity = TigerPkiIdentityLoader.loadIdentity(parsedInfo);

    assertThat(identity).isNotNull();
    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
  }

  @Test
  void compactFormat_multikeyKeystore_aliasOnlyFormat_shouldUseDefaultPasswords() {
    // Test alias-only format with multikey keystore: "keystore.p12;alias;PKCS12"
    String keystorePath = "src/test/resources/multikey.p12";
    String alias = "key1";

    String compactFormat = keystorePath + ";" + alias + ";PKCS12";

    TigerPkiIdentityInformation parsedInfo =
        TigerPkiIdentityLoader.parseInformationString(compactFormat);

    assertThat(parsedInfo.getFilenames()).containsExactly(keystorePath);
    assertThat(parsedInfo.getStoreType()).isEqualTo(TigerPkiIdentityLoader.StoreType.PKCS12);
    assertThat(parsedInfo.getAliasesOrPasswords()).containsExactly(alias);

    TigerPkiIdentity identity = TigerPkiIdentityLoader.loadIdentity(parsedInfo);

    assertThat(identity).isNotNull();
    assertThat(identity.getCertificate()).isNotNull();
    assertThat(identity.getPrivateKey()).isNotNull();
  }

  @Test
  void multikeyKeystore_verifyDifferentKeysLoadedForDifferentAliases() {
    String keystorePath = "src/test/resources/multikey.p12";
    String password = "gematik";

    String compactFormat1 = keystorePath + ";" + password + ";key1;PKCS12";
    String compactFormat2 = keystorePath + ";" + password + ";key2;PKCS12";

    TigerPkiIdentityInformation info1 =
        TigerPkiIdentityLoader.parseInformationString(compactFormat1);
    TigerPkiIdentityInformation info2 =
        TigerPkiIdentityLoader.parseInformationString(compactFormat2);

    TigerPkiIdentity identity1 = TigerPkiIdentityLoader.loadIdentity(info1);
    TigerPkiIdentity identity2 = TigerPkiIdentityLoader.loadIdentity(info2);

    assertThat(identity1).isNotNull();
    assertThat(identity2).isNotNull();
    assertThat(identity1.getPrivateKey()).isNotNull();
    assertThat(identity2.getPrivateKey()).isNotNull();

    assertThat(identity1.getPrivateKey()).isNotEqualTo(identity2.getPrivateKey());
  }

  @Test
  void multikeyKeystore_configurationFormat_shouldWorkWithAliases() {
    TigerGlobalConfiguration.initialize();

    String yamlConfig =
        """
        key1Config:
          filename: src/test/resources/multikey.p12
          password: gematik
          alias: key1
        key2Config:
          filename: src/test/resources/multikey.p12
          password: gematik
          alias: key2
        """;

    TigerGlobalConfiguration.readFromYaml(yamlConfig);

    val key1Config =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "key1Config");
    val key2Config =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "key2Config");
    assertThat(key1Config).isPresent();
    assertThat(key2Config).isPresent();
    TigerConfigurationPkiIdentity identity1 = key1Config.get();
    TigerConfigurationPkiIdentity identity2 = key2Config.get();

    assertThat(identity1.getPrivateKey()).isNotNull();
    assertThat(identity2.getPrivateKey()).isNotNull();

    assertThat(identity1.getFileLoadingInformation().getAlias()).isEqualTo("key1");
    assertThat(identity2.getFileLoadingInformation().getAlias()).isEqualTo("key2");

    assertThat(identity1.getPrivateKey()).isNotEqualTo(identity2.getPrivateKey());
  }

  @Test
  void multikeyKeystore_certificateChains_shouldLoadAndValidateCorrectly() {
    TigerGlobalConfiguration.initialize();

    String yamlConfig =
        """
        chainAlias1Config:
          filename: src/test/resources/multikey.p12
          password: gematik
          alias: chainalias1-noroot
        chainAlias2Config:
          filename: src/test/resources/multikey.p12
          password: gematik
          alias: chainalias2-noroot
        """;

    TigerGlobalConfiguration.readFromYaml(yamlConfig);

    val chainAlias1Config =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "chainAlias1Config");
    val chainAlias2Config =
        TigerGlobalConfiguration.instantiateConfigurationBean(
            TigerConfigurationPkiIdentity.class, "chainAlias2Config");

    assertThat(chainAlias1Config).isPresent();
    assertThat(chainAlias2Config).isPresent();

    TigerConfigurationPkiIdentity identity1 = chainAlias1Config.get();
    TigerConfigurationPkiIdentity identity2 = chainAlias2Config.get();

    // Validate that both identities are loaded correctly
    assertThat(identity1.getPrivateKey()).isNotNull();
    assertThat(identity2.getPrivateKey()).isNotNull();
    assertThat(identity1.getCertificate()).isNotNull();
    assertThat(identity2.getCertificate()).isNotNull();

    // Validate aliases are correct
    assertThat(identity1.getFileLoadingInformation().getAlias()).isEqualTo("chainalias1-noroot");
    assertThat(identity2.getFileLoadingInformation().getAlias()).isEqualTo("chainalias2-noroot");

    // Validate that the certificate chains are different
    assertThat(identity1.getPrivateKey()).isNotEqualTo(identity2.getPrivateKey());
    assertThat(identity1.getCertificate()).isNotEqualTo(identity2.getCertificate());
    assertThat(identity1.getCertificate().getSerialNumber())
        .isNotEqualTo(identity2.getCertificate().getSerialNumber());

    // Validate that the certificate chains themselves are different
    assertThat(identity1.getCertificateChain()).isNotEqualTo(identity2.getCertificateChain());
    assertThat(identity1.getCertificateChain()).isNotEmpty();
    assertThat(identity2.getCertificateChain()).isNotEmpty();
    assertThat(identity1.getCertificateChain()).hasSize(3);
    assertThat(identity2.getCertificateChain()).hasSize(3);
  }

  @Data
  public static class NestedKeyClass {
    private final TigerConfigurationPkiIdentity myKey;
  }
}
