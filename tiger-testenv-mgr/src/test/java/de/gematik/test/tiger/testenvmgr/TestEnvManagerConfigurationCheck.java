/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.util.Map;
import kong.unirest.UnirestInstance;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
@Getter
public class TestEnvManagerConfigurationCheck extends AbstractTestTigerTestEnvMgr {

    // -----------------------------------------------------------------------------------------------------------------
    //
    // check missing mandatory props are detected
    // check key twice in yaml leads to exception
    //
    // -----------------------------------------------------------------------------------------------------------------
    @ParameterizedTest
    @CsvSource({
        "testDocker,type",
        "testDocker,source",
        "testDocker,version",
        "testTigerProxy,type",
        "testExternalJar,type",
        "testExternalJar,source",
        "testExternalUrl,type",
        "testExternalUrl,source"})
    public void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(String cfgFile, String prop) {
        createTestEnvMgrSafelyAndExecute(
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml",
            envMgr -> {
                CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFile);
                ReflectionTestUtils.setField(srv, prop, null);
                assertThatThrownBy(() -> TigerServer.create("blub", srv, mockTestEnvMgr())
                    .assertThatConfigurationIsCorrect())
                    .isInstanceOf(TigerTestEnvException.class);
            });
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n" +
        "  testTigerProxy:\n" +
        "    type: tigerProxy\n" +
        "    tigerProxyCfg:\n" +
        "      adminPort: 9999", skipEnvironmentSetup = true)
    public void testCheckCfgPropertiesMissingParamMandatoryServerPortProp_NOK(TigerTestEnvMgr envMgr) {
        CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
        assertThatThrownBy(() -> TigerServer.create("testTigerProxy", srv, mockTestEnvMgr())
            .assertThatConfigurationIsCorrect())
            .isInstanceOf(TigerTestEnvException.class);
    }

    @Test
    public void testCheckDoubleKey_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDoubleKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .hasRootCauseMessage("Duplicate keys in yaml file ('serverDouble')!");
    }

    @Test
    public void testCheckDeprecatedKey_port_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .getRootCause()
            .hasMessageContaining("The key ('port') in yaml file should not be used anymore, use 'proxyPort' instead!");
    }

    @Test
    public void testCheckDeprecatedKey_tigerport_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .getRootCause()
            .hasMessageContaining("The key ('port') in yaml file should not be used anymore, use 'proxyPort' instead!");
    }
    @Test
    public void testCheckDeprecatedKey_serverPort_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .getRootCause()
            .hasMessageContaining("The key ('serverPort') in yaml file should not be used anymore, use 'adminPort' instead!");
   }

    @Test
    public void testCheckDeprecatedKey_proxyCfg_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .getRootCause()
            .hasMessageContaining("The key ('proxyCfg') in yaml file should not be used anymore, it is omitted!");
    }

    @Test
    public void testCheckDeprecatedKey_healthcheck_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .getRootCause()
            .hasMessageContaining("The key ('tiger.servers.*.externalJarOptions.healthcheck') in yaml file should not be used anymore, use 'tiger.servers.*.healthcheckUrl' instead!");
    }

    @Test
    public void testCheckDeprecatedKey_healthcheckurl_NOK() {
        assertThatThrownBy(() -> TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDeprecatedKey.yaml")))
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .getRootCause()
            .hasMessageContaining("The key ('tiger.servers.*.externalJarOptions.healthcheckurl') in yaml file should not be used anymore, use 'tiger.servers.*.healthcheckUrl' instead!");
    }

    @Test
    @TigerTest(tigerYaml = "servers:\n" +
        "  tigerServer1:\n"
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
        + "      proxyPort: ${free.port.4}\n",
        additionalProperties = {"custom.value = ftp"})
    public void testPlaceholderAndExports(TigerTestEnvMgr envMgr) {
        final TigerServer tigerServer2 = envMgr.getServers().get("tigerServer2");
        assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getAdminPort())
            .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.3").get());
        assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getProxyPort())
            .isEqualTo(TigerGlobalConfiguration.readIntegerOptional("free.port.4").get());
        assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getProxiedServerProtocol())
            .isEqualTo("ftp");
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // invalid general props
    //

    @Test
    public void testCreateInvalidInstanceType() {
        TigerGlobalConfiguration.readFromYaml("servers:\n"
                + "  testInvalidType:\n"
                + "    type: NOTEXISTING\n"
                + "    source:\n"
                + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/",
            "tiger");
        TigerGlobalConfiguration.setRequireTigerYaml(false);

        assertThatThrownBy(() -> {
            final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
            envMgr.setUpEnvironment();
        }).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateUnknownTemplate() {
        TigerGlobalConfiguration.setRequireTigerYaml(false);
        TigerGlobalConfiguration.readFromYaml("servers:\n"
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
    public void testCreateInvalidPkiKeys_wrongType() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_wrongType.yaml"));
        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateInvalidPkiKeys_missingCertificate() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_missingCertificate.yaml"));

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
    }

    @Test
    public void testCreateInvalidPkiKeys_emptyCertificate() {
        TigerGlobalConfiguration.initializeWithCliProperties(Map.of("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_emptyCertificate.yaml"));

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
    }

    @Test
    public void testInvalidUrlMappings_noArrow() {
        TigerGlobalConfiguration.setRequireTigerYaml(false);
        TigerGlobalConfiguration.readFromYaml("servers:\n"
                + "  testInvalidUrlMappings_noArrow:\n"
                + "    type: externalUrl\n"
                + "    source:\n"
                + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/\n"
                + "    urlMappings:\n"
                + "      - https://bla\n",
            "tiger");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("The urlMappings configuration 'https://bla' is not correct. Please check your .yaml-file.");
    }

    @Test
    public void testInvalidUrlMappings_noDestinationRoute() {
        TigerGlobalConfiguration.setRequireTigerYaml(false);
        TigerGlobalConfiguration.readFromYaml("servers:\n"
                + "  testInvalidUrlMappings_noDestinationRoute:\n"
                + "    type: externalUrl\n"
                + "    source:\n"
                + "      - https://idp-test.zentral.idp.splitdns.ti-dienste.de/\n"
                + "    urlMappings:\n"
                + "      - https://bla -->",
            "tiger");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("The urlMappings configuration 'https://bla -->' is not correct. Please check your .yaml-file.");
    }

    @Test
    @TigerTest(tigerYaml =
        "additionalYamls:\n"
            + "  - filename: src/test/resources/de/gematik/test/tiger/testenvmgr/testExternalJar.yaml\n"
            + "    baseKey: tiger\n")
    public void readAdditionalYamlFiles(UnirestInstance unirestInstance) {
        assertThat(unirestInstance.get("http://testExternalJar").asString().isSuccess())
            .isTrue();
    }

    @Test
    @TigerTest(tigerYaml =
        "additionalYamls:\n"
            + "  - filename: src/test/resources/de/gematik/test/tiger/testenvmgr/externalJarWithAdditionalTigerKey.yaml\n")
    public void readAdditionalYamlFilesWithoutBaseKey(UnirestInstance unirestInstance) {
        assertThat(unirestInstance.get("http://testExternalJar").asString().isSuccess())
            .isTrue();
    }

    @Test
    @TigerTest(tigerYaml =
        "additionalYamls:\n"
            + "  - filename: src/test/resources/externalConfiguration.yaml\n"
            + "    baseKey: foobar\n")
    public void readAdditionalYamlFilesWithDifferingBaseKey() {
        assertThat(TigerGlobalConfiguration.readString("foobar.some.keys"))
            .isEqualTo("andValues");
    }

    @Test
    @TigerTest(tigerYaml =
        "additionalYamls:\n"
            + "  - filename: src/test/resources/defineFooAsBar.yaml\n"
            + "  - filename: src/test/resources/${foo}.yaml\n"
            + "    baseKey: baseKey\n")
    public void readAdditionalYamlFilesWithPlaceholdersInName() {
        assertThat(TigerGlobalConfiguration.readString("baseKey.someKey"))
            .isEqualTo("someValue");
    }

}
