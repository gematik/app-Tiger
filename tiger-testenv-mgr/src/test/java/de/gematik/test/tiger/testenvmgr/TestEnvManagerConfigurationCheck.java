/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
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
          "servers:\n"
              + "  testTigerProxy:\n"
              + "    type: tigerProxy\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: 9999",
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
          "servers:\n"
              + "  tigerServer1:\n"
              + "    hostname: testReverseProxy\n"
              + "    type: tigerProxy\n"
              + "    exports: \n"
              + "      - FOO_BAR=${custom.value}\n"
              + "      - OTHER_PORT=${FREE_PORT_3}\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: ${FREE_PORT_1}\n"
              + "      proxyPort: ${FREE_PORT_2}\n"
              + "  tigerServer2:\n"
              + "    hostname: ${foo.bar}\n"
              + "    type: tigerProxy\n"
              + "    dependsUpon: tigerServer1\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: ${free.port.3}\n"
              + "      proxiedServerProtocol: ${FOO_BAR}\n"
              + "      proxyPort: ${free.port.4}\n"
              + "localProxyActive: false",
      additionalProperties = {"custom.value = ftp"})
  void testPlaceholderAndExports(TigerTestEnvMgr envMgr) {
    final AbstractTigerServer tigerServer2 = envMgr.getServers().get("tigerServer2");
    assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getAdminPort())
        .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.3").get());
    assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getProxyPort())
        .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.4").get());
    assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getProxiedServerProtocol())
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
        "servers:\n"
            + "  testInvalidType:\n"
            + "    type: NOTEXISTING\n"
            + "    source:\n"
            + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/",
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
        "servers:\n"
            + "  unknownTemplate:\n"
            + "    template: some_template_that_does_not_exist\n"
            + "    type: externalUrl\n"
            + "    source:\n"
            + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/",
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
        "servers:\n"
            + "  testInvalidUrlMappings_noArrow:\n"
            + "    type: externalUrl\n"
            + "    source:\n"
            + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/\n"
            + "    urlMappings:\n"
            + "      - https://bla\n",
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
        "servers:\n"
            + "  testInvalidUrlMappings_noDestinationRoute:\n"
            + "    type: externalUrl\n"
            + "    source:\n"
            + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/\n"
            + "    urlMappings:\n"
            + "      - https://bla -->",
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
          "additionalYamls:\n"
              + "  - filename: src/test/resources/externalConfiguration.yaml\n"
              + "    baseKey: foobar\n"
              + "localProxyActive: false")
  void readAdditionalYamlFilesWithDifferingBaseKey() {
    assertThat(TigerGlobalConfiguration.readString("foobar.some.keys")).isEqualTo("andValues");
  }

  @Test
  @TigerTest(
      tigerYaml =
          "additionalYamls:\n"
              + "  - filename: src/test/resources/defineFooAsBar.yaml\n"
              + "  - filename: src/test/resources/${foo}.yaml\n"
              + "    baseKey: baseKey\n"
              + "localProxyActive: false")
  void readAdditionalYamlFilesWithPlaceholdersInName() {
    assertThat(TigerGlobalConfiguration.readString("baseKey.someKey")).isEqualTo("someValue");
  }

  @Test
  void readAdditionalYamlFileFromParentFolder() {
    TigerGlobalConfiguration.initializeWithCliProperties(
        Map.of(
            "TIGER_TESTENV_CFGFILE",
            "src/test/resources/additionalAndTigerYamlCurrentDir/tiger.yaml"));

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
            "src/test/resources/additionalAndTigerYamlCurrentDir/tiger.yaml"));

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
          "servers:\n"
              + "  testTigerProxy:\n"
              + "    type: tigerProxy\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: 9999",
      skipEnvironmentSetup = true)
  void defaultForLocalTigerProxyShouldBeBlockingMode(TigerTestEnvMgr envMgr) {
    envMgr.startLocalTigerProxyIfActivated();
    CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
    assertThat(srv.getTigerProxyCfg().isParsingShouldBlockCommunication()).isFalse();
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
          "servers:\n"
              + "  tigerServer1:\n"
              + "    type: tigerProxy\n"
              + "    exports: \n"
              + "      - OTHER_PORT=${FREE_PORT_3}\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: ${FREE_PORT_1}\n"
              + "      proxyPort: ${FREE_PORT_2}\n"
              + "  tigerServer2:\n"
              + "    type: tigerProxy\n"
              + "    dependsUpon: tigerServer1\n"
              + "    tigerProxyCfg:\n"
              + "      adminPort: ${OTHER_PORT}\n"
              + "      proxyPort: ${free.port.4}\n"
              + "localProxyActive: false\n")
  void testDelayedEvaluation(TigerTestEnvMgr envMgr) {
    assertThat(
            envMgr
                .getServers()
                .get("tigerServer2")
                .getConfiguration()
                .getTigerProxyCfg()
                .getAdminPort())
        .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.3").get());
  }

  @Test
  @TigerTest(
      tigerYaml =
          "servers:\n"
              + "  google:\n"
              + "    type: externalUrl\n"
              + "    source:\n"
              + "      - https://www.google.com\n"
              + "localProxyActive: true\n")
  void testExtUrlServer_healthCheckUrlDefaultsToSource(TigerTestEnvMgr envMgr) {
    assertThat(envMgr.getServers().get("google").getStatus()).isEqualTo(TigerServerStatus.RUNNING);
    final UnirestInstance instance = Unirest.spawnInstance();
    instance.config().proxy("127.0.0.1", envMgr.getLocalTigerProxyOrFail().getProxyPort());
    assertThat(instance.get("http://google").asString().getStatus()).isEqualTo(200);
    instance.close();
  }
}
