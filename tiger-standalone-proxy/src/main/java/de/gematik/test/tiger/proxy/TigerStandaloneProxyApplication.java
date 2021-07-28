/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import de.gematik.test.tiger.proxy.configuration.TigerProxyConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
public class TigerStandaloneProxyApplication {

    private final ApplicationConfiguration applicationConfiguration;

    public static void main(String[] args) { //NOSONAR
        // Necessary hack to avoid mockserver activating java.util.logging - this will not work in combination
        // with spring boot!
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");

        SpringApplication.run(TigerStandaloneProxyApplication.class, args);
    }

    @Bean
    public TigerProxy tigerProxy() {
        if (applicationConfiguration.getProxy() != null) {
            return new TigerProxy(applicationConfiguration.getProxy());
        } else {
            return new TigerProxy(new TigerProxyConfiguration());
        }
    }
}
