package de.gematik.test.tiger.proxy;

import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class TigerProxyConfigurator {

    private final TigerProxy tigerProxy;
    private final ServerProperties serverProperties;

    @PostConstruct
    public void init() {
        tigerProxy.addRoute("http://tiger.proxy",
            "http://localhost:" + serverProperties.getPort());
    }
}
