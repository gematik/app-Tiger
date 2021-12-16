package de.gematik.test.tiger.proxy;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.gematik.test.tiger.common.config.tigerProxy.ForwardProxyInfo;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.config.tigerProxy.TigerProxyType;
import de.gematik.test.tiger.common.exceptions.TigerProxyToForwardProxyException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class TigerProxyConfigurationTest extends AbstractTigerProxyTest {

    @BeforeEach
    public void init() {
        setOrClearProperty("http.proxyHost", null);
        setOrClearProperty("http.proxyPort", null);
        setOrClearProperty("http.proxyUser", null);
        setOrClearProperty("http.proxyPassword", null);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"http://localhost:80", "http://localhost"})
    public void httpProxyWithoutAuthAsEnvVar_shouldBeSet(String httpProxyWithoutAuthEnv,
        CapturedOutput capturedOutput) {
        setOrClearProperty("http.proxyHost", null);
        setOrClearProperty("http.proxyPort", null);
        setOrClearProperty("http.proxyUser", null);
        setOrClearProperty("http.proxyPassword", null);

        withEnvironmentVariable("http_proxy", httpProxyWithoutAuthEnv)
            .execute(() -> {
                spawnTigerProxyWith(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build());
                ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
                    .createMockServerProxyConfiguration().get();
                assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
                assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);

                assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80");
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"http://username:password@localhost:80", "http://username:password@localhost"})
    public void httpProxyWithAuthAsEnvVar_shouldBeSet(String httpProxyWithAuthEnv, CapturedOutput capturedOutput) {
        withEnvironmentVariable("http_proxy", httpProxyWithAuthEnv)
            .execute(() -> {
                spawnTigerProxyWith(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build());
                ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
                    .createMockServerProxyConfiguration().get();
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
    public void httpProxyWithMissingParamsAsEnvVar_shouldNotBeSet(String httpProxyEnvWithoutType) {
        withEnvironmentVariable("http_proxy", httpProxyEnvWithoutType)
            .execute(() -> {
                assertThatThrownBy(() -> TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build().getForwardToProxy().createMockServerProxyConfiguration().get())
                    .isInstanceOf(TigerProxyToForwardProxyException.class);
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost, 80", "localhost, null"},
        nullValues = {"null"})
    public void httpProxyWithoutAuthUsingSystemProperties_shouldBeSet(String proxyHost, String proxyPort,
        CapturedOutput capturedOutput) {
        System.setProperty("http.proxyHost", proxyHost);
        setOrClearProperty("http.proxyPort", proxyPort);

        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .hostname("$SYSTEM")
                .build())
            .build());

        ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
            .createMockServerProxyConfiguration().get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo(proxyHost);
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80");
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {"localhost, 80, username, password", "localhost, null, username, password"},
        nullValues = {"null"})
    public void httpProxyWithAuthUsingSystemProperties_shouldBeSet(String proxyHost, String proxyPort,
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
        ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
            .createMockServerProxyConfiguration().get();
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
    public void httpProxyUsingSystemPropertiesMissingAuth_shouldNotBeSet(
        String proxyHost, String proxyPort, String proxyUser, String proxyPassword
    ) {
        withEnvironmentVariable("http_proxy", null)
            .execute(() -> {
                setOrClearProperty("http.proxyHost", proxyHost);
                setOrClearProperty("http.proxyPort", proxyPort);
                setOrClearProperty("http.proxyUser", proxyUser);
                setOrClearProperty("http.proxyPassword", proxyPassword);

                assertThatThrownBy(() -> TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build().getForwardToProxy().createMockServerProxyConfiguration().get())
                    .isInstanceOf(TigerProxyToForwardProxyException.class);
            });
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvSource(value = {
        "null, 80, username, password",
    },
        nullValues = {"null"})
    public void httpProxyUsingSystemPropertiesMissingHost_shouldBeEmpty(
        String proxyHost, String proxyPort, String proxyUser, String proxyPassword
    ) {
        withEnvironmentVariable("http_proxy", null)
            .execute(() -> {
                setOrClearProperty("http.proxyHost", proxyHost);
                setOrClearProperty("http.proxyPort", proxyPort);
                setOrClearProperty("http.proxyUser", proxyUser);
                setOrClearProperty("http.proxyPassword", proxyPassword);

                assertThat(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build().getForwardToProxy().createMockServerProxyConfiguration()).isEmpty();
            });
    }

    @SneakyThrows
    @Test
    public void httpProxySystemPropertiesNoParams_shouldBeEmpty() {
        withEnvironmentVariable("http_proxy", null)
            .execute(() -> {
                assertThat(TigerProxyConfiguration.builder()
                    .forwardToProxy(ForwardProxyInfo.builder()
                        .hostname("$SYSTEM")
                        .build())
                    .build().getForwardToProxy().createMockServerProxyConfiguration()).isEmpty();
            });
    }

    @SneakyThrows
    @Test
    public void httpProxyWithParameters_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTP)
                .hostname("localhost")
                .port(80)
                .username("username")
                .password("password")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
            .createMockServerProxyConfiguration().get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
        assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80@username:password");
    }

    @SneakyThrows
    @Test
    public void httpProxyWithParametersWithoutPort_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTP)
                .hostname("localhost")
                .username("username")
                .password("password")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
            .createMockServerProxyConfiguration().get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);
        assertThat(proxyConfiguration.getUsername()).isEqualTo("username");
        assertThat(proxyConfiguration.getPassword()).isEqualTo("password");

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80@username:password");
    }

    @SneakyThrows
    @Test
    public void httpProxyWithParametersWithoutUsernamePasswordPort_shouldBeSet(CapturedOutput capturedOutput) {
        spawnTigerProxyWith(TigerProxyConfiguration.builder()
            .forwardToProxy(ForwardProxyInfo.builder()
                .type(TigerProxyType.HTTP)
                .hostname("localhost")
                .build())
            .build());
        ProxyConfiguration proxyConfiguration = tigerProxy.getTigerProxyConfiguration().getForwardToProxy()
            .createMockServerProxyConfiguration().get();
        assertThat(proxyConfiguration.getProxyAddress().getHostName()).isEqualTo("localhost");
        assertThat(proxyConfiguration.getProxyAddress().getPort()).isEqualTo(80);

        assertThat(capturedOutput.getOut()).contains("Forward proxy is set to HTTP://localhost:80");
    }

    @SneakyThrows
    @Test
    public void httpProxyEmpty_shouldNotBeSet(CapturedOutput capturedOutput) {
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
