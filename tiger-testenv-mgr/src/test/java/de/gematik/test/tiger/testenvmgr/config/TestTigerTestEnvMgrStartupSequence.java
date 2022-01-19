/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.config;

import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.servers.TigerServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.github.stefanbirkner.systemlambda.SystemLambda.withEnvironmentVariable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Slf4j
public class TestTigerTestEnvMgrStartupSequence {

    private static List<String> startupSequence = new ArrayList<>();
    private static TigerTestEnvMgr envMgr;

    @BeforeAll
    public static void setUp() throws Exception {
        envMgr = buildTestEnvMgr();
    }

    public static Stream<Arguments> checkSuccessfullStartupSequencesParameters() {
        return Stream.of(
            // master <- intermediate <- leaf
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("masterServer", ""),
                buildServerMockDependingUpon("intermediate", "masterServer"),
                buildServerMockDependingUpon("leaf", "intermediate")
            ), List.of(List.of("masterServer", "intermediate", "leaf"))),

            // master1  master2
            //       ^  ^
            //       leaf
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("master1", ""),
                buildServerMockDependingUpon("master2", ""),
                buildServerMockDependingUpon("leaf", "master1,master2")
            ), List.of(List.of("master1", "master2", "leaf"), List.of("master2", "master1", "leaf"))),

            //             master
            //             ^  ^
            // intermediate1  intermediate2
            //             ^  ^
            //             leaf
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("masterServer", ""),
                buildServerMockDependingUpon("intermediate1", "masterServer"),
                buildServerMockDependingUpon("intermediate2", "masterServer"),
                buildServerMockDependingUpon("leaf", "intermediate1, intermediate2")
            ), List.of(List.of("masterServer", "intermediate1", "intermediate2", "leaf"),
                List.of("masterServer", "intermediate2", "intermediate1", "leaf"))),

            // master1(wait till leaf 2 is up)   master2
            //    ^                                 ^
            //  leaf1                            leaf2
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("master1", "", "leaf2"),
                buildServerMockDependingUpon("leaf1", "master1"),
                buildServerMockDependingUpon("master2", ""),
                buildServerMockDependingUpon("leaf2", "master2")
            ), List.of(
                List.of("master1", "master2", "leaf2", "leaf1"),
                List.of("master2", "master1", "leaf2", "leaf1"),
                List.of("master2", "leaf2", "master1", "leaf1"))),

            //            master1      master2 (wait until intermediate1 is up)
            //            ^    ^        ^
            // intermediate1  intermediate2
            //             ^  ^
            //             leaf
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("master1", ""),
                buildServerMockDependingUpon("master2", "", "intermediate1"),
                buildServerMockDependingUpon("intermediate1", "master1"),
                buildServerMockDependingUpon("intermediate2", "master1,master2"),
                buildServerMockDependingUpon("leaf", "intermediate1, intermediate2")
            ), List.of(List.of("master2", "master1", "intermediate1", "intermediate2", "leaf"),
                List.of("master1", "master2", "intermediate1", "intermediate2", "leaf"),
                List.of("master1", "intermediate1", "master2", "intermediate2", "leaf")))
        );
    }

    public static Stream<Arguments> cyclicGraphParameters() {
        return Stream.of(
            // a <-> b
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("a", "b"),
                buildServerMockDependingUpon("b", "a")
            )),

            // a -> b <-> c
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("a", "b"),
                buildServerMockDependingUpon("b", "c"),
                buildServerMockDependingUpon("c", "b")
            )),

            // a -> b
            //  ^  V
            //    c
            Arguments.of(Map.ofEntries(
                buildServerMockDependingUpon("a", "b"),
                buildServerMockDependingUpon("b", "c"),
                buildServerMockDependingUpon("c", "a")
            ))
        );
    }

    private static Map.Entry<String, TigerServer> buildServerMockDependingUpon(String name, String dependsUpon) {
        final CfgServer configuration = new CfgServer();
        configuration.setDependsUpon(dependsUpon);
        configuration.setType(ServerType.EXTERNALURL);
        configuration.setSource(List.of("blub"));
        final TigerServer server = new MockTigerServer(name, configuration, envMgr);

        return Pair.of(name, server);
    }

    private static Map.Entry<String, TigerServer> buildServerMockDependingUpon(String name, String dependsUpon,
                                                                               String delayStartupUntilThisServerIsRunning) {
        final CfgServer configuration = new CfgServer();
        configuration.setDependsUpon(dependsUpon);
        configuration.setType(ServerType.EXTERNALURL);
        configuration.setSource(List.of("blub"));
        final TigerServer server = new MockTigerServer(name, configuration, envMgr, delayStartupUntilThisServerIsRunning);

        return Pair.of(name, server);
    }

    private static TigerTestEnvMgr buildTestEnvMgr() throws Exception {
        AtomicReference<TigerTestEnvMgr> env = new AtomicReference<>();
        withEnvironmentVariable("TIGER_TESTENV_CFGFILE",
            "src/test/resources/de/gematik/test/tiger/testenvmgr/testNoTigerProxy.yaml")
            .execute(() -> env.set(new TigerTestEnvMgr()));
        return env.get();
    }

    @BeforeEach
    public void reset() {
        startupSequence.clear();
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfullStartupSequencesParameters")
    public void checkSuccessfullStartupSequences(Map<String, TigerServer> serverMap,
                                                 List<List<String>> startupSequences) {
        ReflectionTestUtils.setField(envMgr, "servers", serverMap);

        envMgr.setUpEnvironment();

        startupSequences.stream()
            .map(potentialOrder -> {
                try {
                    assertThat(this.startupSequence)
                        .isEqualTo(potentialOrder);
                    return Optional.empty();
                } catch (AssertionError e) {
                    return Optional.of(e);
                }
            })
            .filter(Optional::isEmpty)
            .findAny()
            .orElseThrow(() -> new AssertionError("Encountered unexpected startup sequence. Found\n"
                + startupSequence + "\nbut wanted one of\n" + startupSequences));
    }

    @ParameterizedTest
    @MethodSource("cyclicGraphParameters")
    public void cyclicGraph_expectError(Map<String, TigerServer> serverMap) {
        ReflectionTestUtils.setField(envMgr, "servers", serverMap);

        assertThatThrownBy(() -> envMgr.setUpEnvironment())
            .isInstanceOf(TigerEnvironmentStartupException.class)
            .hasMessageContaining("Cyclic graph");
    }

    @Test
    public void dependsUponNonExistingServer_shouldFail() {
        ReflectionTestUtils.setField(envMgr, "servers", Map.ofEntries(
            buildServerMockDependingUpon("a", "b")));

        assertThatThrownBy(() -> envMgr.setUpEnvironment())
            .isInstanceOf(TigerEnvironmentStartupException.class)
            .hasMessageContaining("Unknown server");
    }

    public static class MockTigerServer extends TigerServer {

        private final Optional<String> delayStartupUntilThisServerIsRunning;

        public MockTigerServer(String name, CfgServer configuration, TigerTestEnvMgr envMgr, String delayStartupUntilThisServerIsRunning) {
            super(name, name, configuration, envMgr);
            this.delayStartupUntilThisServerIsRunning = Optional.ofNullable(delayStartupUntilThisServerIsRunning);
        }

        public MockTigerServer(String name, CfgServer configuration, TigerTestEnvMgr envMgr) {
            super(name, name, configuration, envMgr);
            this.delayStartupUntilThisServerIsRunning = Optional.empty();
        }

        @Override
        public void performStartup() {
            synchronized (startupSequence) {
                startupSequence.add(getHostname());
            }
            log.info("Starting server {}", getHostname());
            if (delayStartupUntilThisServerIsRunning.isPresent()) {
                await()
                    .atMost(1, TimeUnit.SECONDS)
                    .pollInterval(Duration.ofMillis(1))
                    .until(() -> this.getTigerTestEnvMgr().getServers().get(delayStartupUntilThisServerIsRunning.get())
                        .getStatus() == TigerServerStatus.RUNNING);
            }
        }

        @Override
        public void shutdown() {

        }
    }
}
