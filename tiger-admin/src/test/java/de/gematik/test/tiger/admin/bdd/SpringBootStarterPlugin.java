package de.gematik.test.tiger.admin.bdd;

import de.gematik.test.tiger.admin.TigerAdminApplication;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestStepStarted;
import java.util.Map;
import java.util.SortedSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.SocketUtils;

@Slf4j
public class SpringBootStarterPlugin implements EventListener {

    private ConfigurableApplicationContext ctx;

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, event -> {
            log.info("Starting tiger-admin...");
            final SortedSet<Integer> ports = SocketUtils.findAvailableTcpPorts(1);
            final int serverPort = ports.first();
            SpringBootDriver.adminPort = serverPort;

            ctx = new SpringApplicationBuilder(TigerAdminApplication.class)
                .properties(Map.of(
                    "server.port", serverPort))
                .web(WebApplicationType.SERVLET)
                .run();
        });

        publisher.registerHandlerFor(TestRunFinished.class, event -> {
            log.info("Stopping tiger-admin");
            if (ctx.isRunning()) {
                ctx.stop();
            }
        });

        publisher.registerHandlerFor(TestStepStarted.class, event -> {
            log.info("starting test-step");
        });
    }
}
