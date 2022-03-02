/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.rbellogger.captures.RbelFileReaderCapturer;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.proxy.configuration.ApplicationConfiguration;
import java.io.File;
import java.util.Base64;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@RequiredArgsConstructor
public class TigerProxyApplication {

    @Getter
    private final ApplicationConfiguration applicationConfiguration;

    public static void main(String[] args) { //NOSONAR
        // Necessary hack to avoid mockserver activating java.util.logging - which would not work in combination
        // with spring boot!
        System.setProperty("java.util.logging.config.file", "SKIP_MOCKSERVER_LOG_INIT!");
        SpringApplication.run(TigerProxyApplication.class, args);
    }

    @SneakyThrows
    @Bean
    public TigerProxy tigerProxy() {
        if (applicationConfiguration != null) {
            return new TigerProxy(applicationConfiguration);
        } else {
            return new TigerProxy(new TigerProxyConfiguration());
        }
    }
}
