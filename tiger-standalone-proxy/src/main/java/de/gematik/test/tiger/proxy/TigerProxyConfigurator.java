package de.gematik.test.tiger.proxy;

import de.gematik.test.tiger.proxy.data.TigerRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TigerProxyConfigurator {

    private final TigerProxy tigerProxy;
    private final ServletWebServerApplicationContext webServerAppCtxt;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        // you might be tempted to look for "server.port", but don't:
        // when it is zero (random free port) then it stays zero
        tigerProxy.addRoute(
                TigerRoute.builder()
                        .from("http://tiger.proxy")
                        .to("http://localhost:" + webServerAppCtxt.getWebServer().getPort())
                        .activateRbelLogging(false)
                        .build());
    }
}
