/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
public class TigerStandaloneProxyApplication {

    private ApplicationConfiguration applicationConfiguration;

    public static void main(String[] args) {
        SpringApplication.run(TigerStandaloneProxyApplication.class, args);
    }

    @Bean
    public TigerProxy tigerProxy() {
        return new TigerProxy(applicationConfiguration.getProxy());
    }
}
