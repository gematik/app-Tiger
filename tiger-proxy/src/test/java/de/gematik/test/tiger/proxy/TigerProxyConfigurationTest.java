/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.data.config.tigerProxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyType;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import de.gematik.test.tiger.common.exceptions.TigerUnknownProtocolException;
import de.gematik.test.tiger.config.ResetTigerConfiguration;
import de.gematik.test.tiger.proxy.configuration.ProxyConfigurationConverter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

@ExtendWith(OutputCaptureExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
@ResetTigerConfiguration
public class TigerProxyConfigurationTest extends AbstractTigerProxyTest {

    @SneakyThrows
    @BeforeEach
    public void init() {
        setOrClearProperty("http.proxyHost", null);
        setOrClearProperty("http.proxyPort", null);
        setOrClearProperty("http.proxyUser", null);
        setOrClearProperty("http.proxyPassword", null);

        setOrClearProperty("https.proxyHost", null);
        setOrClearProperty("https.proxyPort", null);
        setOrClearProperty("https.proxyUser", null);
        setOrClearProperty("https.proxyPassword", null);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"http://localhost:80", "http://localhost"})
    void httpProxyWithoutAuthAsEnvVar_shouldBeSet(String httpProxyWithoutAuthEnv,
        CapturedOutput capturedOutput) {
        new EnvironmentVariables("http_proxy", httpProxyWithoutAuthEnv)
            .and("https_proxy", null)
            .execute(() -> {
                spawnTigerProxyWith(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build());
                ForwardProxyInfo fwInfo = tigerProxy.getTigerProxyConfiguration().getForwardToProxy();
                ProxyConfiguration proxyConfiguration = ProxyConfigurationConverter.createMockServerProxyConfiguration(fwInfo).get();
                assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
                assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);

                assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80");
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"http://username:password@localhost:80", "http://username:password@localhost"})
    void httpProxyWithAuthAsEnvVar_shouldBeSet(String httpProxyWithAuthEnv, CapturedOutput capturedOutput) {
        new EnvironmentVariables("http_proxy", httpProxyWithAuthEnv)
            .and("https_proxy", null)
            .execute(() -> {
                spawnTigerProxyWith(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build());
                ProxyConfiguration proxyConfiguration =
                    ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
                assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
                assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);
                assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
                assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

                assertThat(capturedOutput.getOut()).contains(
                    "Forward proxy is set to HTTP://localhost:80@username:password");
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost", "http://username@localhost:80", "http://password@localhost:80"})
    void httpProxyWithMissingParamsAsEnvVar_shouldNotBeSet(String httpProxyEnvWithoutType) {
        new EnvironmentVariables("http_proxy", httpProxyEnvWithoutType)
            .and("https_proxy", null)
            .execute(() -> {
                assertThatThrownBy(() -> ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy()).get())
                    .isInstanceOf(TigerProxyToForwardProxyException.class);
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost, 80", "localhost, null"},
        nullValues = {"null"})
    void httpProxyWithoutAuthUsingSystemProperties_shouldBeSet(String proxyHost, String proxyPort,
        CapturedOutput capturedOutput) {
        System.setProperty("http.proxyHost", proxyHost);
        setOrClearProperty("http.proxyPort", proxyPort);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .hostname("$SYSTEM")
                .build())
            .build());

        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo(proxyHost);
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {
        "localhost, 80, username, password",
        "localhost, null, username, password"},
        nullValues = {"null"})
    void httpProxyWithAuthUsingSystemProperties_shouldBeSet(String proxyHost, String proxyPort,
        String proxyUser, String proxyPassword, CapturedOutput capturedOutput) {
        System.setProperty("http.proxyHost", proxyHost);
        setOrClearProperty("http.proxyPort", proxyPort);
        System.setProperty("http.proxyUser", proxyUser);
        System.setProperty("http.proxyPassword", proxyPassword);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .hostname("$SYSTEM")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo(proxyHost);
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);
        assertThat(proxyConfiguration.getUsername()).isEqualTo(proxyUser);
        assertThat(proxyConfiguration.getPassword()).isEqualTo(proxyPassword);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80@username:password");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {
        "locahost, 80, null, password",
        "locahost, 80, username, null"
    },
        nullValues = {"null"})
    void httpProxyUsingSystemPropertiesMissingAuth_shouldNotBeSet(
        String proxyHost, String proxyPort, String proxyUser, String proxyPassword
    ) {
        new EnvironmentVariables("http_proxy", null)
            .execute(() -> {
                setOrClearProperty("http.proxyHost", proxyHost);
                setOrClearProperty("http.proxyPort", proxyPort);
                setOrClearProperty("http.proxyUser", proxyUser);
                setOrClearProperty("http.proxyPassword", proxyPassword);

                assertThatThrownBy(() -> ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy()).get())
                    .isInstanceOf(TigerProxyToForwardProxyException.class);
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {
        "null, 80, username, password",
    },
        nullValues = {"null"})
    void httpProxyUsingSystemPropertiesMissingHost_shouldBeEmpty(
        String proxyHost, String proxyPort, String proxyUser, String proxyPassword
    ) {
        new EnvironmentVariables("http_proxy", null)
            .and("https_proxy", null)
            .execute(() -> {
                setOrClearProperty("http.proxyHost", proxyHost);
                setOrClearProperty("http.proxyPort", proxyPort);
                setOrClearProperty("http.proxyUser", proxyUser);
                setOrClearProperty("http.proxyPassword", proxyPassword);

                assertThat(ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy())).isEmpty();
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"https://localhost:443", "https://localhost"})
    void httpsProxyWithoutAuthAsEnvVar_shouldBeSet(String httpsProxyWithoutAuthEnv,
        CapturedOutput capturedOutput) {
        new EnvironmentVariables("https_proxy", httpsProxyWithoutAuthEnv)
            .and("http_proxy", null)
            .execute(() -> {
                spawnTigerProxyWith(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build());
                ProxyConfiguration proxyConfiguration =
                    ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
                assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
                assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);

                assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTPS://localhost:443");
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"https://username:password@localhost:443", "https://username:password@localhost"})
    void httpsProxyWithAuthAsEnvVar_shouldBeSet(String httpsProxyWithAuthEnv, CapturedOutput capturedOutput) {
        new EnvironmentVariables("https_proxy", httpsProxyWithAuthEnv)
            .and("http_proxy", null)
            .execute(() -> {
                spawnTigerProxyWith(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build());
                ProxyConfiguration proxyConfiguration =
                    ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
                assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
                assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);
                assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
                assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

                assertThat(capturedOutput.getOut()).contains(
                    "Forward proxy is set to HTTPS://localhost:443@username:password");
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost", "https://username@localhost:443", "https://password@localhost:443"})
    void httpsProxyWithMissingParamsAsEnvVar_shouldNotBeSet(String httpsProxyEnvWithoutType) {
        new EnvironmentVariables("https_proxy", httpsProxyEnvWithoutType)
            .and("http_proxy", null)
            .execute(() -> {
                assertThatThrownBy(() -> ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build().getForwardToProxy()).get())
                    .isInstanceOf(TigerProxyToForwardProxyException.class);
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost, 443", "localhost, null"},
        nullValues = {"null"})
    void httpsProxyWithoutAuthUsingSystemProperties_shouldBeSet(String proxyHost, String proxyPort,
        CapturedOutput capturedOutput) {
        System.setProperty("https.proxyHost", proxyHost);
        setOrClearProperty("https.proxyPort", proxyPort);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .hostname("$SYSTEM")
                .build())
            .build());

        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo(proxyHost);
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTPS://localhost:443");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost, 443, username, password", "localhost, null, username, password"},
        nullValues = {"null"})
    void httpsProxyWithAuthUsingSystemProperties_shouldBeSet(String proxyHost, String proxyPort,
        String proxyUser, String proxyPassword, CapturedOutput capturedOutput) {
        System.setProperty("https.proxyHost", proxyHost);
        setOrClearProperty("https.proxyPort", proxyPort);
        System.setProperty("https.proxyUser", proxyUser);
        System.setProperty("https.proxyPassword", proxyPassword);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .hostname("$SYSTEM")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo(proxyHost);
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);
        assertThat(proxyConfiguration.getUsername()).isEqualTo(proxyUser);
        assertThat(proxyConfiguration.getPassword()).isEqualTo(proxyPassword);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTPS://localhost:443@username:password");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {
        "locahost, 443, null, password",
        "locahost, 443, username, null"
    },
        nullValues = {"null"})
    void httpsProxyUsingSystemPropertiesMissingAuth_shouldNotBeSet(
        String proxyHost, String proxyPort, String proxyUser, String proxyPassword
    ) {
        new EnvironmentVariables("https_proxy", null)
            .and("http_proxy", null)
            .execute(() -> {
                setOrClearProperty("https.proxyHost", proxyHost);
                setOrClearProperty("https.proxyPort", proxyPort);
                setOrClearProperty("https.proxyUser", proxyUser);
                setOrClearProperty("https.proxyPassword", proxyPassword);

                assertThatThrownBy(() -> ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy()).get())
                    .isInstanceOf(TigerProxyToForwardProxyException.class);
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {
        "null, 443, username, password",
    },
        nullValues = {"null"})
    void httpsProxyUsingSystemPropertiesMissingHost_shouldBeEmpty(
        String proxyHost, String proxyPort, String proxyUser, String proxyPassword
    ) {
        new EnvironmentVariables("https_proxy", null)
            .and("http_proxy", null)
            .execute(() -> {
                setOrClearProperty("https.proxyHost", proxyHost);
                setOrClearProperty("https.proxyPort", proxyPort);
                setOrClearProperty("https.proxyUser", proxyUser);
                setOrClearProperty("https.proxyPassword", proxyPassword);

                assertThat(ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy())).isEmpty();
            });
    }

    @SneakyThrows
    @Test
    void httpProxyWithParameters_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTP)
                .hostname("localhost")
                .port(80)
                .username("username")
                .password("password")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
        assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80@username:password");
    }

    @SneakyThrows
    @Test
    void httpsProxyWithParameters_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTPS)
                .hostname("localhost")
                .port(443)
                .username("username")
                .password("password")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
        assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTPS://localhost:443@username:password");
    }

    @SneakyThrows
    @Test
    void httpProxyWithParametersWithoutPort_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTP)
                .hostname("localhost")
                .username("username")
                .password("password")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
        assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80@username:password");
    }

    @SneakyThrows
    @Test
    void httpsProxyWithParametersWithoutPort_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTPS)
                .hostname("localhost")
                .username("username")
                .password("password")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
        assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTPS://localhost:443@username:password");
    }

    @SneakyThrows
    @Test
    void httpProxyWithParametersWithoutUsernamePasswordPort_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTP)
                .hostname("localhost")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80");
    }

    @SneakyThrows
    @Test
    void httpsProxyWithParametersWithoutUsernamePasswordPort_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTPS)
                .hostname("localhost")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration =
            ProxyConfigurationConverter.createMockServerProxyConfiguration(tigerProxy.getTigerProxyConfiguration().getForwardToProxy()).get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(443);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTPS://localhost:443");
    }

    @SneakyThrows
    @Test
    void systemProxyNothingSet_shouldBeEmpty() {
        new EnvironmentVariables("https_proxy", null)
            .and("http_proxy", null)
            .execute(() -> {
                assertThat(ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy())).isEmpty();
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"abcd://localhost:443"})
    void proxyAsEnvVarWithMiscellaneousProtocol_shouldNotBeSet(String httpsProxyEnvWithoutType) {
        new EnvironmentVariables("https_proxy", httpsProxyEnvWithoutType)
            .and("http_proxy", null)
            .execute(() -> {
                assertThatThrownBy(() -> ProxyConfigurationConverter.createMockServerProxyConfiguration(TigerProxyConfiguration.builder()
                        .forwardToProxy(ForwardProxyInfo.builder()
                            .hostname("$SYSTEM")
                            .build())
                        .build().getForwardToProxy()).get())
                    .isInstanceOf(TigerUnknownProtocolException.class);
            });
    }

    @SneakyThrows
    @Test
    void emptyProxy_shouldNotBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .build())
            .build());

        assertThat(capturedOutput.getOut()).contains("Tigerproxy has NO forward proxy configured!");
    }

    private void setOrClearProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
