/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.common.data.config.tigerProxy.TigerRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TigerProxyConfigurator {

    private final TigerProxy tigerProxy;
    private final ServletWebServerApplicationContext webServerAppCtxt;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (tigerProxy.getTigerProxyConfiguration().getAdminPort() == 0) {
            tigerProxy.getTigerProxyConfiguration().setAdminPort(webServerAppCtxt.getWebServer().getPort());
        }
        log.info("Adding route for 'http://tiger.proxy'...");
        tigerProxy.addRoute(
                TigerRoute.builder()
                        .from("http://tiger.proxy")
                            // you might be tempted to look for "server.port", but don't:
                            // when it is zero (random free port) then it stays zero
                        .to("http://localhost:" + webServerAppCtxt.getWebServer().getPort())
                        .disableRbelLogging(true)
                        .internalRoute(true)
                        .build());
    }
}
