/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockserver.model.HttpRequest.request;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.TigerConfigurationException;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import kong.unirest.Unirest;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.SocketUtils;

@Slf4j
@Getter
public class TestEnvManagerConfigurationCheck extends AbstractTestTigerTestEnvMgr {

    static Stream<Arguments> cfgFileAndMandatoryPropertyProvider() {
        return Stream.of(
            arguments("testDocker", "type"),
            arguments("testDocker", "source"),
            arguments("testDocker", "version"),
            arguments("testTigerProxy", "type"),
            arguments("testExternalJar", "type"),
            arguments("testExternalJar", "source"),
            arguments("testExternalUrl", "type"),
            arguments("testExternalUrl", "source")
        );
    }

    // -----------------------------------------------------------------------------------------------------------------
    //
    // check missing mandatory props are detected
    // check key twice in yaml leads to exception
    //
    // -----------------------------------------------------------------------------------------------------------------
    @ParameterizedTest
    @MethodSource("cfgFileAndMandatoryPropertyProvider")
    public void testCheckCfgPropertiesMissingParamMandatoryProps_NOK(String cfgFile, String prop) {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/" + cfgFile + ".yaml");

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get(cfgFile);
            ReflectionTestUtils.setField(srv, prop, null);
            assertThatThrownBy(() -> TigerServer.create("blub", srv, null)
                .assertThatConfigurationIsCorrect())
                .isInstanceOf(TigerTestEnvException.class);
        });
    }

    @Test
    public void testCheckCfgPropertiesMissingParamMandatoryServerPortProp_NOK() {
        TigerGlobalConfiguration.initialize();
        TigerGlobalConfiguration.readFromYaml(
            "servers:\n" +
                "  testTigerProxy:\n" +
                "    type: tigerProxy\n" +
                "    tigerProxyCfg:\n" +
                "      serverPort: 9999", "tiger");

        createTestEnvMgrSafelyAndExecute(envMgr -> {
            CfgServer srv = envMgr.getConfiguration().getServers().get("testTigerProxy");
            assertThatThrownBy(() -> TigerServer.create("testTigerProxy", srv, null)
                .assertThatConfigurationIsCorrect())
                .isInstanceOf(TigerTestEnvException.class);
        });
    }

    @Test
    public void testCheckDoubleKey_NOK() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testDoubleKey.yaml");

        assertThatThrownBy(TigerTestEnvMgr::new)
            .hasRootCauseInstanceOf(TigerConfigurationException.class)
            .hasRootCauseMessage("Duplicate keys in yaml file ('serverDouble')!");
    }



    @Test
    public void testPlaceholderAndExports() {
        var freePorts = IteratorUtils.toList(SocketUtils.findAvailableTcpPorts(4).iterator());
        String yamlSource =
            "servers:\n" +
                "  tigerServer1:\n"
                + "    hostname: testReverseProxy\n"
                + "    type: tigerProxy\n"
                + "    exports: \n"
                + "      - FOO_BAR=${custom.value}\n"
                + "      - OTHER_PORT=${FREE_PORT_3}\n"
                + "    tigerProxyCfg:\n"
                + "      serverPort: ${FREE_PORT_1}\n"
                + "      proxyCfg:\n"
                + "        port: ${FREE_PORT_2}\n"
                + "  tigerServer2:\n"
                + "    hostname: ${foo.bar}\n"
                + "    type: tigerProxy\n"
                + "    dependsUpon: tigerServer1\n"
                + "    tigerProxyCfg:\n"
                + "      serverPort: ${free.port.3}\n"
                + "      proxiedServerProtocol: ${FOO_BAR}\n"
                + "      proxyCfg:\n"
                + "        port: ${free.port.4}\n";

        TigerGlobalConfiguration.readFromYaml(yamlSource, "tiger");
        TigerGlobalConfiguration.putValue("free.port.1", freePorts.get(0));
        TigerGlobalConfiguration.putValue("free.port.2", freePorts.get(1));
        TigerGlobalConfiguration.putValue("free.port.3", freePorts.get(2));
        TigerGlobalConfiguration.putValue("free.port.4", freePorts.get(3));
        TigerGlobalConfiguration.putValue("custom.value", "ftp");

        TigerGlobalConfiguration.initialize();
        createTestEnvMgrSafelyAndExecute(envMgr -> {
            envMgr.setUpEnvironment();

            final TigerServer tigerServer2 = envMgr.getServers().get("tigerServer2");
            assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getServerPort())
                .isEqualTo(freePorts.get(2));
            assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getProxyCfg().getPort())
                .isEqualTo(freePorts.get(3));
            assertThat(tigerServer2.getConfiguration().getTigerProxyCfg().getProxiedServerProtocol())
                .isEqualTo("ftp");
        });
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

        assertThatThrownBy(() -> {
            final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
            envMgr.setUpEnvironment();
        }).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateUnknownTemplate() {
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
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_wrongType.yaml");
        assertThatThrownBy(TigerTestEnvMgr::new).isInstanceOf(TigerConfigurationException.class);
    }

    @Test
    public void testCreateInvalidPkiKeys_missingCertificate() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_missingCertificate.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
    }

    @Test
    public void testCreateInvalidPkiKeys_emptyCertificate() {
        TigerGlobalConfiguration.putValue("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testInvalidPkiKeys_emptyCertificate.yaml");

        final TigerTestEnvMgr envMgr = new TigerTestEnvMgr();
        assertThatExceptionOfType(TigerConfigurationException.class).isThrownBy(() -> {
            envMgr.setUpEnvironment();
        }).withMessage("Your certificate is empty, please check your .yaml-file for disc_sig");
    }

    @Test
    public void testInvalidUrlMappings_noArrow() {
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
}
