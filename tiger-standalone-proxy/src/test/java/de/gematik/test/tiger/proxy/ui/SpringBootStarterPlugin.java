package de.gematik.test.tiger.proxy.ui;

import de.gematik.test.tiger.proxy.TigerStandaloneProxyApplication;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.SocketUtils;

import java.util.Map;
import java.util.SortedSet;

@Slf4j
public class SpringBootStarterPlugin implements EventListener {

    private ConfigurableApplicationContext ctx;

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, event -> {
            log.info("Starting tiger-proxy...");
            final SortedSet<Integer> ports = SocketUtils.findAvailableTcpPorts(2);
            final int serverPort = ports.first();
            final int proxyPort = ports.last();
            UiTest.proxyPort = proxyPort;
            UiTest.adminPort = serverPort;

            ctx = new SpringApplicationBuilder(TigerStandaloneProxyApplication.class)
                .properties(Map.of(
                    "server.port", serverPort,
                    "tigerproxy.port", proxyPort))
                .web(WebApplicationType.SERVLET)
                .run();
        });

        publisher.registerHandlerFor(TestRunFinished.class, event -> {
            log.info("Stopping tiger-proxy");
            if (ctx.isRunning()) {
                ctx.stop();
            }
        });
    }
}
