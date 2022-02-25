/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class TigerProxyStartupErrorsTest {
// test adapted from
// https://stackoverflow.com/questions/31692863/what-is-the-best-way-to-test-that-a-spring-application-context-fails-to-start

    @Test
    public void shouldLoadCorrectKeystore() {
        ApplicationContextRunner contextRunner = tigerProxyStandaloneContext()
            .withPropertyValues("tigerProxy.serverIdentity=hera.p12;00");

        contextRunner.run((context) -> assertThat(context).hasNotFailed());
    }

    @Test
    public void noCertificateChainSupplied_shouldProduceError() {
        ApplicationContextRunner contextRunner = tigerProxyStandaloneContext()
            .withPropertyValues("tigerProxy.tls.serverIdentity=tls_cert.jks;gematik");

        contextRunner.run((context) -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure().getMessage())
                .contains("Configured server-identity has no valid chain");
        });
    }

    private ApplicationContextRunner tigerProxyStandaloneContext() {
        return new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean("webServerAppCtxt", ServletWebServerApplicationContext.class,
                () -> mock(ServletWebServerApplicationContext.class))
            .withConfiguration(AutoConfigurations.of(TigerProxyApplication.class));
    }
}
