package de.gematik.test.tiger.proxy;

import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TigerProxyConfigurator {

    private final TigerProxy tigerProxy;
    private final ServletWebServerApplicationContext webServerAppCtxt;

    @PostConstruct
    public void init() {
        // you might be tempted to look for "server.port", but don't:
        // when it is zero (random free port) then it stays zero
        tigerProxy.addRoute("http://tiger.proxy",
            "http://localhost:" + webServerAppCtxt.getWebServer().getPort());
    }
}
