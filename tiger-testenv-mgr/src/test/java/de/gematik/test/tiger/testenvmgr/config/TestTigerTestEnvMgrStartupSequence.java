/*
 * Copyright 2024 gematik GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.gematik.test.tiger.testenvmgr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.test.tiger.common.config.SourceType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.ExternalUrlServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

@Slf4j
class TestTigerTestEnvMgrStartupSequence {

  private static final List<String> startupSequence = new ArrayList<>();
  private static TigerTestEnvMgr envMgr;

  @BeforeAll
  public static void setUp() {
    envMgr = buildTestEnvMgr();
  }

  @AfterAll
  public static void tearDown() {
    TigerGlobalConfiguration.reset();
  }

  public static Stream<Arguments> checkSuccessfullStartupSequencesParameters() {
    return Stream.of(
        // master <- intermediate <- leaf
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("masterServer", ""),
                buildServerMockDependingUpon("intermediate", "masterServer"),
                buildServerMockDependingUpon("leaf", "intermediate")),
            List.of(List.of("masterServer", "intermediate", "leaf"))),

        // master1  master2
        //       ^  ^
        //       leaf
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("master1", ""),
                buildServerMockDependingUpon("master2", ""),
                buildServerMockDependingUpon("leaf", "master1,master2")),
            List.of(List.of("master1", "master2", "leaf"), List.of("master2", "master1", "leaf"))),

        //             master
        //             ^  ^
        // intermediate1  intermediate2
        //             ^  ^
        //             leaf
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("masterServer", ""),
                buildServerMockDependingUpon("intermediate1", "masterServer"),
                buildServerMockDependingUpon("intermediate2", "masterServer"),
                buildServerMockDependingUpon("leaf", "intermediate1, intermediate2")),
            List.of(
                List.of("masterServer", "intermediate1", "intermediate2", "leaf"),
                List.of("masterServer", "intermediate2", "intermediate1", "leaf"))),

        // master1(wait till leaf 2 is up)   master2
        //    ^                                 ^
        //  leaf1                            leaf2
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("master1", "", "leaf2"),
                buildServerMockDependingUpon("leaf1", "master1"),
                buildServerMockDependingUpon("master2", ""),
                buildServerMockDependingUpon("leaf2", "master2")),
            List.of(
                List.of("master1", "master2", "leaf2", "leaf1"),
                List.of("master2", "master1", "leaf2", "leaf1"),
                List.of("master2", "leaf2", "master1", "leaf1"))),

        //            master1      master2 (wait until intermediate1 is up)
        //            ^    ^        ^
        // intermediate1  intermediate2
        //             ^  ^
        //             leaf
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("master1", ""),
                buildServerMockDependingUpon("master2", "", "intermediate1"),
                buildServerMockDependingUpon("intermediate1", "master1"),
                buildServerMockDependingUpon("intermediate2", "master1,master2"),
                buildServerMockDependingUpon("leaf", "intermediate1, intermediate2")),
            List.of(
                List.of("master2", "master1", "intermediate1", "intermediate2", "leaf"),
                List.of("master1", "master2", "intermediate1", "intermediate2", "leaf"),
                List.of("master1", "intermediate1", "master2", "intermediate2", "leaf"))));
  }

  public static Stream<Arguments> cyclicGraphParameters() {
    return Stream.of(
        // a <-> b
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("a", "b"), buildServerMockDependingUpon("b", "a"))),

        // a -> b <-> c
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("a", "b"),
                buildServerMockDependingUpon("b", "c"),
                buildServerMockDependingUpon("c", "b"))),

        // a -> b
        //  ^  V
        //    c
        Arguments.of(
            Map.ofEntries(
                buildServerMockDependingUpon("a", "b"),
                buildServerMockDependingUpon("b", "c"),
                buildServerMockDependingUpon("c", "a"))));
  }

  @SneakyThrows
  private static Map.Entry<String, AbstractTigerServer> buildServerMockDependingUpon(
      String name, String dependsUpon) {
    final CfgServer configuration = new CfgServer();
    configuration.setDependsUpon(dependsUpon);
    configuration.setType("mockserver");
    configuration.setSource(List.of("blub"));
    final AbstractTigerServer server = new MockTigerServer(name, configuration, envMgr);
    TigerGlobalConfiguration.readFromYaml(
        new ObjectMapper().writeValueAsString(configuration),
        SourceType.TEST_CONTEXT,
        "tiger",
        "servers",
        name);

    return Pair.of(name, server);
  }

  @SneakyThrows
  private static Map.Entry<String, AbstractTigerServer> buildServerMockDependingUpon(
      String name, String dependsUpon, String delayStartupUntilThisServerIsRunning) {
    final CfgServer configuration = new CfgServer();
    configuration.setDependsUpon(dependsUpon);
    configuration.setType(ExternalUrlServer.class.getAnnotation(TigerServerType.class).value());
    configuration.setSource(List.of("blub"));
    final AbstractTigerServer server =
        new MockTigerServer(name, configuration, envMgr, delayStartupUntilThisServerIsRunning);
    TigerGlobalConfiguration.readFromYaml(
        new ObjectMapper().writeValueAsString(configuration),
        SourceType.TEST_CONTEXT,
        "tiger",
        "servers",
        name);

    return Pair.of(name, server);
  }

  private static TigerTestEnvMgr buildTestEnvMgr() {
    TigerGlobalConfiguration.reset();
    TigerGlobalConfiguration.readFromYaml(
        "cfgfile: \"src/test/resources/de/gematik/test/tiger/testenvmgr/testNoTigerProxy.yaml\"",
        "tiger",
        "testenv");
    return new TigerTestEnvMgr();
  }

  @BeforeEach
  public void reset() {
    startupSequence.clear();
  }

  @ParameterizedTest
  @MethodSource("checkSuccessfullStartupSequencesParameters")
  void checkSuccessfullStartupSequences(
      Map<String, AbstractTigerServer> serverMap, List<List<String>> startupSequences) {
    ReflectionTestUtils.setField(envMgr, "servers", serverMap);
    ReflectionTestUtils.setField(envMgr, "isShuttingDown", false);
    ReflectionTestUtils.setField(envMgr, "fixedPoolExecutor", Executors.newCachedThreadPool());
    ReflectionTestUtils.setField(envMgr, "cachedExecutor", Executors.newCachedThreadPool());
    try {
      envMgr.setUpEnvironment();
      log.info("Completed startup normally, now checking the order");

      startupSequences.stream()
          .map(
              potentialOrder -> {
                try {
                  assertThat(potentialOrder).isEqualTo(startupSequence);
                  return Optional.empty();
                } catch (AssertionError e) {
                  return Optional.of(e);
                }
              })
          .filter(Optional::isEmpty)
          .findAny()
          .orElseThrow(
              () ->
                  new AssertionError(
                      "Encountered unexpected startup sequence. Found\n"
                          + startupSequence
                          + "\nbut wanted one of\n"
                          + startupSequences));
    } finally {
      envMgr.shutDown();
    }
  }

  @ParameterizedTest
  @MethodSource("cyclicGraphParameters")
  void cyclicGraph_expectError(Map<String, AbstractTigerServer> serverMap) {
    ReflectionTestUtils.setField(envMgr, "servers", serverMap);

    assertThatThrownBy(() -> envMgr.setUpEnvironment())
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("Cyclic graph");
  }

  @Test
  void dependsUponNonExistingServer_shouldFail() {
    ReflectionTestUtils.setField(
        envMgr, "servers", Map.ofEntries(buildServerMockDependingUpon("a", "b")));

    assertThatThrownBy(() -> envMgr.setUpEnvironment())
        .isInstanceOf(TigerEnvironmentStartupException.class)
        .hasMessageContaining("Unknown server");
  }

  @TigerServerType("mockserver")
  public static class MockTigerServer extends AbstractTigerServer {

    private final Optional<String> delayStartupUntilThisServerIsRunning;

    public MockTigerServer(
        String name,
        CfgServer configuration,
        TigerTestEnvMgr envMgr,
        String delayStartupUntilThisServerIsRunning) {
      super(name, name, envMgr, configuration);
      this.delayStartupUntilThisServerIsRunning =
          Optional.ofNullable(delayStartupUntilThisServerIsRunning);
    }

    public MockTigerServer(String name, CfgServer configuration, TigerTestEnvMgr envMgr) {
      super(name, name, envMgr, configuration);
      this.delayStartupUntilThisServerIsRunning = Optional.empty();
    }

    @Override
    public void performStartup() {
      synchronized (startupSequence) {
        startupSequence.add(getHostname());
      }
      log.info("Starting server {}, current sequence is {}", getHostname(), startupSequence);
      delayStartupUntilThisServerIsRunning.ifPresent(
          s ->
              await()
                  .atMost(1, TimeUnit.SECONDS)
                  .pollInterval(Duration.ofMillis(1))
                  .until(
                      () ->
                          this.getTigerTestEnvMgr().getServers().get(s).getStatus()
                              == TigerServerStatus.RUNNING));
    }

    @Override
    public void shutdown() {}
  }
}
