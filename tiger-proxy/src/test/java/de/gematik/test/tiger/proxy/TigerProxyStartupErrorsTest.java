/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

public class TigerProxyStartupErrorsTest {
// test adapted from
// https://stackoverflow.com/questions/31692863/what-is-the-best-way-to-test-that-a-spring-application-context-fails-to-start

    @Test
    public void shouldLoadCorrectKeystore() {
        ApplicationContextRunner contextRunner = tigerProxyStandaloneContext()
            .withPropertyValues("tigerProxy.tls.serverIdentity=src/test/resources/hera.p12;00");

        contextRunner.run((context) -> assertThat(context).hasNotFailed());
    }

    private ApplicationContextRunner tigerProxyStandaloneContext() {
        return new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withBean("webServerAppCtxt", ServletWebServerApplicationContext.class,
                () -> mock(ServletWebServerApplicationContext.class))
            .withConfiguration(AutoConfigurations.of(TigerProxyApplication.class));
    }
}
