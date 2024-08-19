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

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.*;

import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.Map;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@Slf4j
@Getter
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension.class)
class TestEnvManagerConfigurationCheck {

  @BeforeEach
  public void resetConfig() {
    TigerGlobalConfiguration.reset();
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // check missing mandatory props are detected
  // check key twice in yaml leads to exception
  //
  // -----------------------------------------------------------------------------------------------------------------
  @ParameterizedTest
  @CsvSource({
    "testTigerProxy,type",
    "testExternalJar,type",
    "testExternalJar,source",
    "testExternalUrl,type",
    "testExternalUrl,source"
  })
  void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(String cfgFile, String prop) {
    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml",
        envMgr -> {
          CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFile);
          ReflectionTestUtils.setField(srv, prop, null);

          assertThatThrownBy( // NOSONAR
                  // some test variants fail on createServer and some on the assert method
                  // so both methods need to be in the clause
                  () -> {
                    var server = envMgr.createServer("blub", srv);
                    server.assertThatConfigurationIsCorrect();
                  })
              .isInstanceOf(TigerTestEnvException.class);
        });
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      servers:
                        testTigerProxy:
                          type: tigerProxy
                          tigerProxyConfiguration:
                            adminPort: 9999""",
      skipEnvironmentSetup = true)
  void testCheckCfgPropertiesMissingParamMandatoryServerPortProp_NOK(TigerTestEnvMgr envMgr) {
    CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
    AbstractTigerServer server = envMgr.createServer("testTigerProxy", srv);
    assertThatThrownBy(server::assertThatConfigurationIsCorrect)
        .isInstanceOf(TigerTestEnvException.class);
  }

  @Test
  void testCheckDoubleKey_NOK() {
    Map<String, String> yamlMap =
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDoubleKey.yaml");
    assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(yamlMap))
        .isInstanceOf(TigerConfigurationException.class)
        .hasMessage("Duplicate keys in yaml file ('serverDouble')!");
  }

  @ParameterizedTest
  @CsvSource({
    "port,proxyPort",
    "serverPort,adminPort",
    "tiger.servers.*.externalJarOptions.healthcheck,tiger.servers.*.healthcheckUrl",
    "tiger.servers.*.externalJarOptions.healthcheckurl,tiger.servers.*.healthcheckUrl"
  })
  void testCheckDeprecatedKeys_NOK(String oldPropertyName, String newPropertyName) {
    Map<String, String> yamlMap =
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml");
    assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(yamlMap))
        .isInstanceOf(TigerConfigurationException.class)
        .hasMessageContaining(
            "The key ('"
                + oldPropertyName
                + "') in yaml file should not be used anymore, "
                + "use '"
                + newPropertyName
                + "' instead!");
  }

  @Test
  void testCheckDeprecatedKey_proxyCfg_NOK() {
    Map<String, String> yamlMap =
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml");
    assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(yamlMap))
        .isInstanceOf(TigerConfigurationException.class)
        .hasMessageContaining(
            "The key ('proxyCfg') in yaml file should not be used anymore, it is omitted!");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      servers:
                        tigerServer1:
                          hostname: testReverseProxy
                          type: tigerProxy
                          exports:\s
                            - FOO_BAR=${custom.value}
                            - OTHER_PORT=${FREE_PORT_203}
                          tigerProxyConfiguration:
                            adminPort: ${FREE_PORT_201}
                            proxyPort: ${FREE_PORT_202}
                        tigerServer2:
                          hostname: ${foo.bar}
                          type: tigerProxy
                          dependsUpon: tigerServer1
                          tigerProxyConfiguration:
                            adminPort: ${free.port.203}
                            proxiedServerProtocol: ${FOO_BAR}
                            proxyPort: ${free.port.204}
                      localProxyActive: false""",
      additionalProperties = {"custom.value = ftp"})
  /**
   * we test here that (1) exports are working as expected (other.port is exported by server 1). (2)
   * exports can be used in subsequent server configs (foo.bar is used by server 2). (3) exports can
   * contain references to other properties which are resolved appropriately (custom.value is
   * referenced)
   */
  void testPlaceholderAndExports(TigerTestEnvMgr envMgr) {
    final AbstractTigerServer tigerServer2 = envMgr.getServers().get("tigerServer2");
    String port203 = TigerGlobalConfiguration.readString("free.port.203");
    assertThat(tigerServer2.getConfiguration().getTigerProxyConfiguration().getAdminPort())
        .asString()
        .isEqualTo(port203);
    assertThat(tigerServer2.getConfiguration().getTigerProxyConfiguration().getProxyPort())
        .asString()
        .isEqualTo(TigerGlobalConfiguration.readString("free.port.204"));
    assertThat(TigerGlobalConfiguration.readString("other.port")).isEqualTo(port203);
    assertThat(
            tigerServer2.getConfiguration().getTigerProxyConfiguration().getProxiedServerProtocol())
        .isEqualTo("ftp");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
            servers:
              httpbinName:
                startupTimeoutSec: 1
                type: ${ENV_VAR}
            """,
      additionalProperties = {"ENV_VAR=httpbin"})
  void testEnvironmentVariables(TigerTestEnvMgr envMgr) {
    final AbstractTigerServer httpbin = envMgr.getServers().get("httpbinName");
    assertThat(httpbin.getServerTypeToken()).isEqualTo("httpbin");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            tls:
              forwardMutualTlsIdentity: "${TIM_KEYSTORE};${TIM_KEYSTORE_PW};00"
          """,
      additionalProperties = {
        "tim.keystore = src\\test\\resources\\egk_aut_keystore.jks",
        "tim.keystore.pw = gematik"
      })
  void testProxyEnvironmentVariables(TigerTestEnvMgr envMgr) {
    assertThat(
            envMgr
                .getLocalTigerProxyOrFail()
                .getTigerProxyConfiguration()
                .getTls()
                .getForwardMutualTlsIdentity()
                .getFileLoadingInformation())
        .isEqualTo("src\\test\\resources\\egk_aut_keystore.jks;gematik;00");
  }

  // -----------------------------------------------------------------------------------------------------------------
  //
  // invalid general props
  //

  @Test
  void testCreateInvalidInstanceType() {
    TigerGlobalConfiguration.readFromYaml(
        """
                    servers:
                      testInvalidType:
                        type: NOTEXISTING
                        source:
                          - https://idp-test.zentral.idp.splitdns.ti-dienste.de/""",
        "tiger");
    TigerGlobalConfiguration.setRequireTigerYaml(false);

    assertThatThrownBy(
            () -> {
              final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
            })
        .isInstanceOf(TigerTestEnvException.class);
  }

  @Test
  void testCreateUnknownTemplate() {
    TigerGlobalConfiguration.setRequireTigerYaml(false);
    TigerGlobalConfiguration.readFromYaml(
        """
                    servers:
                      unknownTemplate:
                        template: some_template_that_does_not_exist
                        type: externalUrl
                        source:
                          - https://idp-test.zentral.idp.splitdns.ti-dienste.de/""",
        "tiger");
    TigerGlobalConfiguration.initialize();
    assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
  }

  @Test
  void testCreateInvalidPkiKeys_wrongType() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_wrongType.yaml"));
    assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
  }

  @Test
  void testCreateInvalidPkiKeys_missingCertificate() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_missingCertificate.yaml"));

    final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
    assertThatExceptionOfType(TigerConfigurationException.class)
        .isThrownBy(envMgr::setUpEnvironment)
        .withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
  }

  @Test
  void testCreateInvalidPkiKeys_emptyCertificate() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_emptyCertificate.yaml"));

    final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
    assertThatExceptionOfType(TigerConfigurationException.class)
        .isThrownBy(envMgr::setUpEnvironment)
        .withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
  }

  @Test
  void testInvalidUrlMappings_noArrow() {
    TigerGlobalConfiguration.setRequireTigerYaml(false);
    TigerGlobalConfiguration.readFromYaml(
        """
                    servers:
                      testInvalidUrlMappings_noArrow:
                        type: externalUrl
                        source:
                          - https://idp-test.zentral.idp.splitdns.ti-dienste.de/
                        urlMappings:
                          - https://bla
                    """,
        "tiger");

    final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
    assertThatExceptionOfType(TigerConfigurationException.class)
        .isThrownBy(envMgr::setUpEnvironment)
        .withMessage(
            "The urlMappings configuration 'https://bla' is not correct. Please check your"
                + " .yaml-file.");
  }

  @Test
  void testInvalidUrlMappings_noDestinationRoute() {
    TigerGlobalConfiguration.setRequireTigerYaml(false);
    TigerGlobalConfiguration.readFromYaml(
        """
                    servers:
                      testInvalidUrlMappings_noDestinationRoute:
                        type: externalUrl
                        source:
                          - https://idp-test.zentral.idp.splitdns.ti-dienste.de/
                        urlMappings:
                          - https://bla -->""",
        "tiger");

    final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
    assertThatExceptionOfType(TigerConfigurationException.class)
        .isThrownBy(envMgr::setUpEnvironment)
        .withMessage(
            "The urlMappings configuration 'https://bla -->' is not correct. Please check your"
                + " .yaml-file.");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      additionalConfigurationFiles:
                        - filename: src/test/resources/externalConfiguration.yaml
                          baseKey: foobar
                      localProxyActive: false""")
  void readAdditionalYamlFilesWithDifferingBaseKey() {
    assertThat(TigerGlobalConfiguration.readString("foobar.some.keys")).isEqualTo("andValues");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      additionalConfigurationFiles:
                        - filename: src/test/resources/defineFooAsBar.yaml
                        - filename: src/test/resources/${foo}.yaml
                          baseKey: baseKey
                      localProxyActive: false""")
  void readAdditionalYamlFilesWithPlaceholdersInName() {
    assertThat(TigerGlobalConfiguration.readString("baseKey.someKey")).isEqualTo("someValue");
  }

  @Test
  void readAdditionalYamlFileFromParentFolder() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/additionalYamlsNotCurrentDir/tiger.yaml"));

    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(TigerGlobalConfiguration.readString("external.someNotNested.notNestedKey"))
              .isEqualTo("andValueToKey");
        });
  }

  @Test
  void readAdditionalYamlFileFromParentNestedFolder() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/additionalYamlsNotCurrentDir/tiger.yaml"));

    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(TigerGlobalConfiguration.readString("nested.someNested.nestedKey"))
              .isEqualTo("nestedValue");
        });
  }

  @Test
  void readAdditionalYamlFileFromNotCurrentDir() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "../tiger-test-lib/src/test/resources/additionalYamlsNotCurrentDir/tiger.yaml"));

    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(
                  TigerGlobalConfiguration.readString(
                      "notCurrentDir.someNotCurrentDir.notCurrentDirKeys"))
              .isEqualTo("andNotCurrentDirValues");
        });
  }

  @Test
  void readAdditionalYamlFileFromNotCurrentDirNested() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "../tiger-test-lib/src/test/resources/additionalYamlsNotCurrentDir/tiger.yaml"));

    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(
                  TigerGlobalConfiguration.readString(
                      "notCurrentDir.nested.someNotCurrentDirNested.notCurrentDirKeysNested"))
              .isEqualTo("notCurrentDirNestedValues");
        });
  }

  @Test
  void readAdditionalYamlFileFromCurrentDir() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of("TIGER_TESTENV_CFGFILE", "src/test/resources/tiger.yaml"));

    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(TigerGlobalConfiguration.readString("sameFolder.just.key"))
              .isEqualTo("andValues");
        });
  }

  @Test
  void readAdditionalYamlFileFromCurrentDirNoTigerYamlSet() {
    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(TigerGlobalConfiguration.readString("rootFolder.someNested.nestedKey"))
              .isEqualTo("nestedValue");
        });
  }

  @Test
  void readAdditionalYamlFileFromCurrentDirNoTigerYamlSetNested() {
    AbstractTestTigerTestEnvMgr.createTestEnvMgrSafelyAndExecute(
        envMgr -> {
          envMgr.setUpEnvironment();
          assertThat(
                  TigerGlobalConfiguration.readString(
                      "rootFolderNested.someNotNested.notNestedKey"))
              .isEqualTo("andValueToKey");
        });
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                  additionalConfigurationFiles:
                    - filename: src/test/resources/envFile.env
                      type: ENV
                  localProxyActive: false""")
  void readAdditionalYamlAsEnvFile() {
    assertThat(TigerGlobalConfiguration.readString("my.happy.key")).isEqualTo("someValue");
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      servers:
                        testTigerProxy:
                          type: tigerProxy
                          tigerProxyConfiguration:
                            adminPort: 9999""",
      skipEnvironmentSetup = true)
  void defaultForLocalTigerProxyShouldBeBlockingMode(TigerTestEnvMgr envMgr) {
    envMgr.startLocalTigerProxyIfActivated();
    CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
    assertThat(srv.getTigerProxyConfiguration().isParsingShouldBlockCommunication()).isFalse();
    assertThat(
            envMgr
                .getLocalTigerProxyOrFail()
                .getTigerProxyConfiguration()
                .isParsingShouldBlockCommunication())
        .isTrue();
  }

  @Test
  @TigerTest(
      tigerYaml = "tigerProxy:\n" + "  parsingShouldBlockCommunication: false",
      skipEnvironmentSetup = true)
  void localTigerProxyConfigurationForNonBlockingModeShouldBePossible(TigerTestEnvMgr envMgr) {
    envMgr.startLocalTigerProxyIfActivated();
    assertThat(
            envMgr
                .getLocalTigerProxyOrFail()
                .getTigerProxyConfiguration()
                .isParsingShouldBlockCommunication())
        .isFalse();
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      servers:
                        tigerServer1:
                          type: tigerProxy
                          exports:\s
                            - OTHER_PORT=${FREE_PORT_3}
                          tigerProxyConfiguration:
                            adminPort: ${FREE_PORT_1}
                            proxyPort: ${FREE_PORT_2}
                        tigerServer2:
                          type: tigerProxy
                          dependsUpon: tigerServer1
                          tigerProxyConfiguration:
                            adminPort: ${OTHER_PORT}
                            proxyPort: ${free.port.4}
                      localProxyActive: false
                      """)
  void testDelayedEvaluation(TigerTestEnvMgr envMgr) {
    assertThat(
            envMgr
                .getServers()
                .get("tigerServer2")
                .getConfiguration()
                .getTigerProxyConfiguration()
                .getAdminPort())
        .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.3").get());
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
                      servers:
                        google:
                          type: externalUrl
                          source:
                            - https://www.google.com
                      localProxyActive: true
                      """)
  void testExtUrlServer_healthCheckUrlDefaultsToSource(TigerTestEnvMgr envMgr) {
    assertThat(envMgr.getServers().get("google").getStatus()).isEqualTo(TigerServerStatus.RUNNING);
    final UnirestInstance instance = Unirest.spawnInstance();
    instance.config().proxy("127.0.0.1", envMgr.getLocalTigerProxyOrFail().getProxyPort());
    assertThat(instance.get("http://google").asString().getStatus()).isEqualTo(200);
    instance.close();
  }
}
