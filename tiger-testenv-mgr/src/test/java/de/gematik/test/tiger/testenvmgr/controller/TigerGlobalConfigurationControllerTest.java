/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ResetTigerConfiguration
class TigerGlobalConfigurationControllerTest {

  @LocalServerPort private int port;

  @BeforeEach
  void setup() {
    TigerGlobalConfiguration.initialize();
  }

  @AfterEach
  void tearDown() {
    TigerGlobalConfiguration.reset();
  }

  private static final String EXPECTED_YAML =
      """
dummy:
  nestedbean:
    foo: nestedFoo
    inner:
      foo: nestedInnerFoo
  string: stringValue
java:
  version: '11'
  version.date: '2021-04-20'
os: Windows NT
os.arch: amd64
os.version: '10.0'
""";

  @Test
  void testGetGlobalConfigurationFile() {
    prefillTigerGlobalConfiguration();
    try (var unirest = Unirest.spawnInstance()) {
      String fileContent =
          unirest
              .get("http://localhost:" + port + "/global_configuration/file")
              .asString()
              .getBody();
      assertThat(fileContent).isEqualTo(EXPECTED_YAML);
    }
  }

  @Test
  void getAndReimport_shouldHaveSameKeysButEverythingAsRuntimeExport() {
    var initialConfiguration = TigerGlobalConfiguration.exportConfiguration();
    assertThat(initialConfiguration).isNotEmpty();

    try (var unirest = Unirest.spawnInstance()) {
      String fileContent =
          unirest
              .get("http://localhost:" + port + "/global_configuration/file")
              .asString()
              .getBody();
      HttpResponse<String> importFileResponse =
          unirest
              .post("http://localhost:" + port + "/global_configuration/file")
              .contentType("application/yaml")
              .body(fileContent)
              .asString();
      assertThat(importFileResponse.getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
    }

    var afterReimport = TigerGlobalConfiguration.exportConfiguration();

    assertThat(afterReimport).hasSameSizeAs(initialConfiguration);

    // all elements have same value but are now all RUNTIME_EXPORTS
    afterReimport.forEach(
        (key, value) -> {
          assertThat(value.getRight()).isEqualTo(initialConfiguration.get(key).getRight());
          assertThat(value.getLeft()).isEqualTo(SourceType.RUNTIME_EXPORT);
        });
  }

  @Test
  void testBrokenYaml_shouldThrowAndNotChangeConfiguration() {
    prefillTigerGlobalConfiguration();
    var initialConfig = TigerGlobalConfiguration.exportConfiguration();
    try (var unirest = Unirest.spawnInstance()) {
      HttpResponse<String> importFileResponse =
          unirest
              .post("http://localhost:" + port + "/global_configuration/file")
              .contentType("application/yaml")
              .body("dummy: this\ndummy: not allowed")
              .asString();

      assertThat(importFileResponse.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(TigerGlobalConfiguration.exportConfiguration()).isEqualTo(initialConfig);
    }
  }

  @Test
  void testImportGlobalConfiguration() {
    TigerGlobalConfiguration.dangerouslyDeleteAllProperties();
    try (var unirest = Unirest.spawnInstance()) {

      HttpResponse<String> importFileResponse =
          unirest
              .post("http://localhost:" + port + "/global_configuration/file")
              .contentType("application/yaml")
              .body(EXPECTED_YAML)
              .asString();

      assertThat(importFileResponse.getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
      assertThat(TigerGlobalConfiguration.exportConfiguration()).hasSize(8);

      assertThat(TigerGlobalConfiguration.readString("dummy.string")).isEqualTo("stringValue");
      assertThat(TigerGlobalConfiguration.readString("dummy.nestedbean.foo"))
          .isEqualTo("nestedFoo");
      assertThat(TigerGlobalConfiguration.readString("dummy.nestedbean.inner.foo"))
          .isEqualTo("nestedInnerFoo");
      assertThat(TigerGlobalConfiguration.readString("java.version")).isEqualTo("11");
      assertThat(TigerGlobalConfiguration.readString("java.version.date")).isEqualTo("2021-04-20");
      assertThat(TigerGlobalConfiguration.readString("os")).isEqualTo("Windows NT");
      assertThat(TigerGlobalConfiguration.readString("os.arch")).isEqualTo("amd64");
      assertThat(TigerGlobalConfiguration.readString("os.version")).isEqualTo("10.0");
    }
  }

  void prefillTigerGlobalConfiguration() {
    TigerGlobalConfiguration.dangerouslyDeleteAllProperties();
    TigerGlobalConfiguration.putValue("java.version", 11, SourceType.DEFAULTS);
    TigerGlobalConfiguration.putValue("java.version.date", "2021-04-20", SourceType.MAIN_YAML);
    TigerGlobalConfiguration.putValue("os", "Windows NT", SourceType.HOST_YAML);
    TigerGlobalConfiguration.putValue("os.arch", "amd64", SourceType.ADDITIONAL_YAML);
    TigerGlobalConfiguration.putValue("os.version", "10.0", SourceType.TEST_YAML);

    TigerGlobalConfiguration.putValue("dummy.string", "stringValue", SourceType.ENV);
    TigerGlobalConfiguration.putValue("dummy.nestedbean.foo", "nestedFoo", SourceType.PROPERTIES);
    TigerGlobalConfiguration.putValue(
        "dummy.nestedbean.inner.foo", "nestedInnerFoo", SourceType.CLI);
  }
}
