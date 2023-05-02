/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.admin.bdd;

import static de.gematik.test.tiger.common.SocketHelper.findFreePort;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.admin.TigerAdminApplication;
import de.gematik.test.tiger.common.Ansi;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Slf4j
public class SpringBootStarterPlugin implements EventListener {

    private ConfigurableApplicationContext ctx;

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestRunStarted.class, event -> {
            log.info("Starting Admin UI...");
            final int serverPort = findFreePort();
            SpringBootDriver.adminPort = serverPort;

            ctx = new SpringApplicationBuilder(TigerAdminApplication.class)
                .bannerMode(Mode.OFF)
                .properties(Map.of(
                    "server.port", serverPort))
                .web(WebApplicationType.SERVLET)
                .run();
            log.info(Ansi.colorize("Tiger Admin UI http://localhost:{}", RbelAnsiColors.BLUE_BOLD), serverPort);
        });

        publisher.registerHandlerFor(TestRunFinished.class, event -> {
            log.info("Stopping Admin UI...");
            if (ctx.isRunning()) {
                ctx.stop();
            }
        });
    }
}
