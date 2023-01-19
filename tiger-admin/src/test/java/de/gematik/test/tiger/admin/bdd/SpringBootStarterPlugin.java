/*
 * Copyright (c) 2023 gematik GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.admin.bdd;

import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.admin.TigerAdminApplication;
import de.gematik.test.tiger.common.Ansi;
import io.cucumber.plugin.EventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestRunStarted;
import io.cucumber.plugin.event.TestStepStarted;
import java.util.Map;
import java.util.SortedSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner.Mode;
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
            log.info("Starting Admin UI...");
            final SortedSet<Integer> ports = SocketUtils.findAvailableTcpPorts(1);
            final int serverPort = ports.first();
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
