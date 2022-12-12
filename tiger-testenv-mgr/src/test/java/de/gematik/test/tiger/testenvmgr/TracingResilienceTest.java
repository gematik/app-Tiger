/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr;

import static de.gematik.test.tiger.common.config.TigerGlobalConfiguration.readIntegerOptional;
import static de.gematik.test.tiger.common.config.TigerGlobalConfiguration.resolvePlaceholders;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.fail;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.tigerProxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.proxy.data.TigerDownloadedMessageFacet;
import de.gematik.test.tiger.testenvmgr.config.tigerProxyStandalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.TigerProxyServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kong.unirest.Unirest;
import kong.unirest.UnirestInstance;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;

@Slf4j
@Tag("de.gematik.test.tiger.common.LongrunnerTest")
class TracingResilienceTest {

    private static final int MASTER_ROUNDS = 1000;
    private static final int MESSAGES_PER_ROUND = 2;
    private ConfigurableApplicationContext aggregatingProxyContext;

    /**
     * Receiving <-- Aggregating <-- Sending (local)
     */
    @Test
    @TigerTest(tigerYaml =
        "tigerProxy:\n"
            + "  adminPort: ${free.port.12}\n"
            + "  proxyPort: ${free.port.22}\n"
            + "  rbelBufferSizeInMb: 50\n"
            + "  name: Sending proxy\n"
            + "\n"
            + "servers:\n"
            + "  receivingTigerProxy:\n"
            + "    type: tigerProxy\n"
            + "    tigerProxyCfg:\n"
            + "      trafficEndpoints:\n"
            + "        - http://localhost:${aggregating.proxy.port}\n"
            + "      adminPort: ${free.port.10}\n"
            + "      proxyPort: ${free.port.20}\n"
            + "      name: Receiving Proxy\n"
            + "      downloadInitialTrafficFromEndpoints: true\n"
            + "      connectionTimeoutInSeconds: 100", skipEnvironmentSetup = true)
    void generateTrafficAndBounceViaRemoteProxy(TigerTestEnvMgr testEnvMgr) throws IOException {
        int aggregatingAdminPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            aggregatingAdminPort = socket.getLocalPort();
        }
        TigerGlobalConfiguration.putValue("aggregating.proxy.port", aggregatingAdminPort);

        bootTigerProxy(aggregatingAdminPort);
        testEnvMgr.setUpEnvironment();

        final UnirestInstance instance = Unirest.spawnInstance();
        instance.config().proxy("127.0.0.1", readIntegerOptional("free.port.22").orElseThrow());
        instance.config().followRedirects(false);

        for (int i = 0; i < MASTER_ROUNDS; i++) {
            randomlyCrashAggregatingProxy();
            randomlyRebootAggregatingProxy(aggregatingAdminPort);
            // this line makes it easier to catch up: we remove the race condition of new traffic coming in
            // WHILE aggregating and receiving proxy are trying to catch up.
            // giveAggregatingProxyTimeToCatchUpIfRunning(testEnvMgr);
            for (int j = 0; j < MESSAGES_PER_ROUND; j++) {
                var randomMarker = RandomStringUtils.randomAlphanumeric(20);
                log.info("Sending message {}", randomMarker);
                instance.get(
                        TigerGlobalConfiguration.resolvePlaceholders(
                            "http://localhost:${free.port.10}/" + randomMarker))
                    .asEmpty();
            }
            giveAggregatingProxyTimeToCatchUpIfRunning(testEnvMgr, i + 1);
            log.info("Sent {} msgs, sending-proxy has {} msgs, receiving-proxy has {} msgs",
                (i + 1) * MESSAGES_PER_ROUND * 2,
                testEnvMgr.getLocalTigerProxy().getRbelMessages().size(),
                getReceivingTigerProxyMessages(testEnvMgr).size());
        }

        if (aggregatingProxyContext == null) {
            bootTigerProxy(aggregatingAdminPort);
        }

        await()
            .atMost(20, TimeUnit.SECONDS)
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> {
                log.info("We sent {} message, intercepted {}, aggregating {}, receiving {}",
                    MASTER_ROUNDS * MESSAGES_PER_ROUND * 2,
                    testEnvMgr.getLocalTigerProxy().getRbelMessages().size(),
                    aggregatingProxyContext.getBean(TigerProxy.class).getRbelMessages().size(),
                    getReceivingTigerProxyMessages(testEnvMgr).size());
                return MASTER_ROUNDS * MESSAGES_PER_ROUND * 2 == getReceivingTigerProxyMessages(testEnvMgr).size();
            });
    }

    private void giveAggregatingProxyTimeToCatchUpIfRunning(TigerTestEnvMgr testEnvMgr, int round) {
        if (aggregatingProxyContext != null) {
            try {
                waitAtMost(10, TimeUnit.SECONDS)
                    .until(() -> testEnvMgr.getLocalTigerProxy().getRbelMessages().size()
                        ==
                        getReceivingTigerProxyMessages(testEnvMgr).size());
            } catch (ConditionTimeoutException e) {
                log.error("We sent {} message, intercepted {}, aggregating {}, receiving {}",
                    round * MESSAGES_PER_ROUND * 2,
                    testEnvMgr.getLocalTigerProxy().getRbelMessagesList().size(),
                    aggregatingProxyContext.getBean(TigerProxy.class).getRbelMessages().size(),
                    getReceivingTigerProxyMessages(testEnvMgr).size());
                final List<RbelElement> sendingMsgs
                    = getLastRequestPaths(testEnvMgr.getLocalTigerProxy().getRbelMessagesList());
                final List<RbelElement> aggregatingMsgs = getLastRequestPaths(
                    aggregatingProxyContext.getBean(TigerProxy.class).getRbelMessagesList());
                final List<RbelElement> receivingMsgs = getLastRequestPaths(
                    ((TigerProxyServer) testEnvMgr.getServers().get("receivingTigerProxy")).getTigerProxy()
                        .getRbelMessagesList());
                for (int i = 0; i < sendingMsgs.size(); i++) {
                    if (makeReadable(sendingMsgs.get(i)).contains("/")) {
                        log.error("{}, {}, {}",
                            makeReadable(sendingMsgs.get(i)),
                            makeReadable(aggregatingMsgs.get(i)),
                            makeReadable(receivingMsgs.get(i)));
                    }
                }
                fail();
            }
        }
    }

    private String makeReadable(RbelElement message) {
        String downloadedMarker = "PUSH";
        if (message.hasFacet(TigerDownloadedMessageFacet.class)) {
            downloadedMarker = "DOWN";
        }
        return Optional.ofNullable(message)
            .map(msg -> msg.getRawStringContent().lines().findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .stream()
            .map(str -> str.split(" "))
            .flatMap(ar -> Stream.of(ar).skip(1).limit(1))
            .filter(s -> s.startsWith("/"))
            .findFirst().orElse("") + " (" + downloadedMarker + ")";
    }

    @NotNull
    private List<RbelElement> getLastRequestPaths(List<RbelElement> rbelMessages) {
        return rbelMessages.stream()
            .skip(rbelMessages.size() - 200)
            .collect(Collectors.toList());
    }

    private void randomlyRebootAggregatingProxy(int aggregatingAdminPort) {
        if (aggregatingProxyContext == null && RandomUtils.nextInt(0, 20) < 1) {
            bootTigerProxy(aggregatingAdminPort);
        }
    }

    private void randomlyCrashAggregatingProxy() {
        if (aggregatingProxyContext != null && RandomUtils.nextInt(0, 30) < 1) {
            log.info("Stopping aggregating proxy...");
            aggregatingProxyContext.getBean(TigerProxy.class).shutdown();
            aggregatingProxyContext.stop();
            aggregatingProxyContext.close();
            aggregatingProxyContext = null;
        }
    }

    private Deque<RbelElement> getReceivingTigerProxyMessages(TigerTestEnvMgr testEnvMgr) {
        return testEnvMgr.findServer("receivingTigerProxy")
            .map(TigerProxyServer.class::cast)
            .map(TigerProxyServer::getTigerProxy)
            .get()
            .getRbelLogger().getMessageHistory();
    }

    private void bootTigerProxy(int aggregatingAdminPort) {
        log.info("Waiting for port {} to be available...", aggregatingAdminPort);
        await()
            .until(() -> {
                try (ServerSocket ignored = new ServerSocket(aggregatingAdminPort)) {
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
        log.info("Starting Aggregating Proxy...");
        CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
        standaloneCfg.setTigerProxy(TigerProxyConfiguration.builder()
            .adminPort(aggregatingAdminPort)
            .trafficEndpoints(List.of(resolvePlaceholders("http://localhost:${free.port.12}")))
            .downloadInitialTrafficFromEndpoints(true)
            .rbelBufferSizeInMb(100)
            .activateRbelParsing(false)
            .connectionTimeoutInSeconds(100)
            .name("Aggregating proxy")
            .build());

        aggregatingProxyContext = new SpringApplicationBuilder(TigerProxyApplication.class)
            .bannerMode(Mode.OFF)
            .properties(new HashMap<>(TigerSerializationUtil.toMap(standaloneCfg)))
            .web(WebApplicationType.SERVLET)
            .run();

        log.info("Started Aggregating Proxy");
    }
}
