package de.gematik.test.tiger.proxy.ui;

import de.gematik.test.tiger.proxy.TigerStandaloneProxyApplication;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunStarted;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.netty.MockServer;
import org.springframework.util.SocketUtils;

import java.util.SortedSet;

@Slf4j
public class SpringBootStarterPlugin implements EventListener {

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, event -> {
            log.info("Received event of class {}", event.getClass().getName());
            final SortedSet<Integer> ports = SocketUtils.findAvailableTcpPorts(2);
            final int serverPort = ports.first();
            final int proxyPort = ports.last();
            UiTest.proxyPort = proxyPort;
            UiTest.adminPort = serverPort;
            TigerStandaloneProxyApplication.main(new String[]{
                "--server.port=" + serverPort,
                "--tigerproxy.port=" + proxyPort});
        });
    }
}
