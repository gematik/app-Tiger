/*
 *
 * Copyright 2021-2025 gematik GmbH
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
 *
 * *******
 *
 * For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
 */
package de.gematik.test.tiger.testenvmgr;

import static de.gematik.test.tiger.common.config.TigerGlobalConfiguration.readIntegerOptional;
import static de.gematik.test.tiger.common.config.TigerGlobalConfiguration.resolvePlaceholders;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

import de.gematik.rbellogger.RbelConversionExecutor;
import de.gematik.rbellogger.RbelConversionPhase;
import de.gematik.rbellogger.RbelConverterPlugin;
import de.gematik.rbellogger.data.RbelElement;
import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.proxy.TigerProxy;
import de.gematik.test.tiger.proxy.TigerProxyApplication;
import de.gematik.test.tiger.testenvmgr.config.tigerproxy_standalone.CfgStandaloneProxy;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testutils.junit.RetryingTest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import kong.unirest.core.Unirest;
import kong.unirest.core.UnirestInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;

/**
 * Pushes traffic through a Sending -&gt; Aggregating -&gt; Receiving proxy mesh under two kinds of
 * disruption, and verifies that every message still arrives exactly once and in the order its
 * sender produced it.
 *
 * <p>The aggregating proxy runs in its own JVM so that it can be killed with SIGKILL. A graceful
 * shutdown drains the queues and closes the sessions properly, which hides every failure mode that
 * only shows up when a connection dies without saying so.
 *
 * <p>Killing it is only half the picture: a dead proxy takes its history with it, so there is
 * barely anything left for the receiving proxy to catch up on afterwards. The second test cuts the
 * link between aggregating and receiving proxy instead and leaves both alive - that is what puts
 * the HTTP catch-up path under load.
 *
 * <p>All knobs are system properties, so the workload can be reshaped without touching the source:
 *
 * <pre>
 * mvn ... -Dtracing.resilience.rounds=200 -Dtracing.resilience.messagesPerRound=6
 *
 * rounds               number of send/crash rounds                     (default 1000)
 * messagesPerRound     requests sent per round                         (default 3)
 * prefillMessages      requests blasted in before the first crash, to  (default 0)
 *                      reach a large history without paying for rounds
 * parallelSenders      senders, each on its own connection; a round    (default 10)
 *                      keeps messagesPerRound of them busy at once,
 *                      rotating so all of them get to send
 * crashOneIn           one in N rounds kills the aggregating proxy     (default 30)
 * rebootOneIn          one in N rounds revives it again                (default 20)
 * cutOneIn             one in N rounds cuts the mesh link between      (default 25)
 *                      aggregating and receiving proxy
 * linkDownMillis       how long such a cut lasts                       (default 4000)
 * pauseBetweenRounds   idle time between two rounds, in millis         (default 0)
 * bufferMb             rbel buffer of sending and aggregating proxy    (default 1024)
 * linkKbPerSecond      throttle the mesh link to this rate,            (default 0)
 *                      0 leaves it at loopback speed
 * silentDropOneIn      one in N rounds drops the link without telling  (default 30)
 *                      anyone: sockets stay open, bytes go nowhere.
 *                      0 = never
 * silentDropMillis     how long such a silent drop lasts               (default 8000)
 * parseDelayMillis     delay every message the receiving proxy parses  (default 0)
 * gracefulKillPercent  share of kills that ask for a clean shutdown    (default 20)
 *                      instead of pulling the plug
 * payloadBytes         body size of a large request, 0 disables them   (default 20480)
 * largeMessagePercent  share of requests carrying that body            (default 25)
 * barrier              wait for the mesh to converge after every round (default false)
 * timeBudgetSeconds    stop the round loop after N seconds, 0 = no cap (default 0)
 * convergenceTimeoutSeconds  how long the receiving proxy may lag behind (default 60)
 * seed                 RNG seed, fixes the crash schedule              (default random)
 * </pre>
 *
 * <p>Every failure report ends with the full property list of the run. That reproduces the
 * workload, not necessarily the failure: the seed pins the crash schedule, while the races these
 * defects live in are not reproducible from it.
 */
@Slf4j
@Tag("de.gematik.test.tiger.common.LongRunnerTest")
class TracingResilienceTest {

  private static final String PREFIX = "tracing.resilience.";

  /** Populated by the property readers below, in declaration order. */
  private static final Map<String, Object> KNOBS = new LinkedHashMap<>();

  private static final int MASTER_ROUNDS = intProperty("rounds", 1000);
  private static final int MESSAGES_PER_ROUND = intProperty("messagesPerRound", 3);
  private static final int PREFILL_MESSAGES = intProperty("prefillMessages", 0);
  private static final int PARALLEL_SENDERS = Math.max(1, intProperty("parallelSenders", 10));
  private static final int CRASH_ONE_IN = intProperty("crashOneIn", 30);
  private static final int REBOOT_ONE_IN = intProperty("rebootOneIn", 20);
  private static final int CUT_ONE_IN = intProperty("cutOneIn", 25);
  private static final int LINK_DOWN_MILLIS = intProperty("linkDownMillis", 4000);
  private static final int PAUSE_BETWEEN_ROUNDS = intProperty("pauseBetweenRounds", 0);
  private static final int BUFFER_MB = intProperty("bufferMb", 1024);
  private static final int LINK_KB_PER_SECOND = intProperty("linkKbPerSecond", 0);
  private static final int SILENT_DROP_ONE_IN = intProperty("silentDropOneIn", 30);
  private static final int SILENT_DROP_MILLIS = intProperty("silentDropMillis", 8000);
  private static final int PARSE_DELAY_MILLIS = intProperty("parseDelayMillis", 0);
  private static final int GRACEFUL_KILL_PERCENT = intProperty("gracefulKillPercent", 20);
  private static final int PAYLOAD_BYTES = intProperty("payloadBytes", 20 * 1024);
  private static final int LARGE_MESSAGE_PERCENT = intProperty("largeMessagePercent", 25);
  private static final int TIME_BUDGET_SECONDS = intProperty("timeBudgetSeconds", 0);
  private static final int CONVERGENCE_TIMEOUT_SECONDS =
      intProperty("convergenceTimeoutSeconds", 60);
  private static final boolean CONVERGENCE_BARRIER = booleanProperty("barrier");
  private static final long SEED = longProperty("seed", System.nanoTime());

  static {
    // The yaml above is resolved through TigerGlobalConfiguration, which reads system properties -
    // so a knob only reaches it once it exists as one, default or not.
    System.setProperty(PREFIX + "bufferMb", String.valueOf(BUFFER_MB));
  }

  private static final Pattern MARKER = Pattern.compile("/sender(\\d+)/messageNumber(\\d+)");
  private static final String LARGE_PAYLOAD = "x".repeat(Math.max(0, PAYLOAD_BYTES));
  private static final Path WORK_DIR = Path.of("target", "tracing-resilience");
  private static final Duration BARRIER_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CONVERGENCE_TIMEOUT =
      Duration.ofSeconds(CONVERGENCE_TIMEOUT_SECONDS);
  private static final Duration PROXY_BOOT_TIMEOUT = Duration.ofMinutes(2);
  private static final Duration PROXY_EXIT_TIMEOUT = Duration.ofSeconds(30);
  private static final int PREVIEW_LIMIT = 400;

  private static int aggregatingProxyGeneration;

  private final Random random = new Random(SEED);
  private final List<Sender> senders = new ArrayList<>();

  /** Which sender opens the next round; see {@link #sendMessages(int)}. */
  private int nextSenderOffset = 0;

  private ExecutorService senderPool;
  private UnirestInstance adminClient;
  private Process aggregatingProxyProcess;
  private Path aggregatingProxyLogFile;
  private TigerProxy receivingProxy;
  private MeshLink meshLink;
  private long meshLinkRestoreAtNanos;
  private long meshLinkResumeAtNanos;
  private int aggregatingAdminPort;

  private static int intProperty(String name, int defaultValue) {
    final int value = Integer.getInteger(PREFIX + name, defaultValue);
    KNOBS.put(name, value);
    return value;
  }

  private static long longProperty(String name, long defaultValue) {
    final long value = Long.getLong(PREFIX + name, defaultValue);
    KNOBS.put(name, value);
    return value;
  }

  private static boolean booleanProperty(String name) {
    final boolean value = Boolean.getBoolean(PREFIX + name);
    KNOBS.put(name, value);
    return value;
  }

  /** A single connection into the mesh, sending strictly one request at a time. */
  @RequiredArgsConstructor
  private static final class Sender {
    private final int index;
    private final UnirestInstance connection;
    private int nextSequenceNumber;
  }

  /** Receiving <-- Aggregating <-- Sending (local) */
  @RetryingTest
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            adminPort: ${free.port.12}
            proxyPort: ${free.port.22}
            rbelBufferSizeInMb: ${tracing.resilience.bufferMb}
            name: Sending proxy
          """,
      skipEnvironmentSetup = true)
  void generateTrafficAndBounceViaRemoteProxy(TigerTestEnvMgr testEnvMgr) throws IOException {
    log.info("Workload: {}", replayProperties());

    Files.createDirectories(WORK_DIR);
    adminClient = Unirest.spawnInstance();
    aggregatingAdminPort = freePort();

    testEnvMgr.setUpEnvironment();
    bootAggregatingProxy();
    startReceivingProxy(aggregatingAdminPort);
    startSenders();

    warmUpMesh(testEnvMgr);
    runRounds(
        testEnvMgr,
        () -> {
          randomlyRebootAggregatingProxy();
          randomlyKillAggregatingProxy();
        });

    if (aggregatingProxyProcess == null) {
      bootAggregatingProxy();
    }
    awaitConvergence(testEnvMgr, CONVERGENCE_TIMEOUT);
    checkMeshTransmittedEverythingExactlyOnce(testEnvMgr);
  }

  /**
   * Same mesh, but nothing is killed - only the link between aggregating and receiving proxy is
   * repeatedly cut. Traffic keeps flowing into the aggregating proxy the whole time, so everything
   * the receiving proxy misses while it is disconnected has to come back through the HTTP catch-up
   * download. A crash cannot produce that situation: the crashed proxy loses the very history the
   * catch-up would read.
   */
  @RetryingTest
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            adminPort: ${free.port.12}
            proxyPort: ${free.port.22}
            rbelBufferSizeInMb: ${tracing.resilience.bufferMb}
            name: Sending proxy
          """,
      skipEnvironmentSetup = true)
  void generateTrafficWhileTheMeshLinkFlaps(TigerTestEnvMgr testEnvMgr) throws IOException {
    log.info("Workload: {}", replayProperties());

    Files.createDirectories(WORK_DIR);
    adminClient = Unirest.spawnInstance();
    aggregatingAdminPort = freePort();
    final int meshLinkPort = freePort();

    testEnvMgr.setUpEnvironment();
    bootAggregatingProxy();
    meshLink = new MeshLink(meshLinkPort, aggregatingAdminPort, LINK_KB_PER_SECOND * 1024);
    meshLink.raise();
    startReceivingProxy(meshLinkPort);
    startSenders();

    warmUpMesh(testEnvMgr);
    runRounds(
        testEnvMgr,
        () -> {
          restoreMeshLinkWhenTheOutageIsOver();
          randomlyCutMeshLink();
          randomlySilentlyDropMeshLink();
        });

    meshLink.removeThrottle();
    meshLink.raise();
    awaitConvergence(testEnvMgr, CONVERGENCE_TIMEOUT);
    checkMeshTransmittedEverythingExactlyOnce(testEnvMgr);
  }

  /**
   * Both disruptions at once, which is what a cluster does to a mesh in practice: the route drops,
   * and while it is gone the proxy behind it is rescheduled. Whatever the receiving proxy has to
   * recover afterwards it has to recover from a proxy that may have restarted in the meantime and
   * rebuilt its own history from upstream first.
   */
  @RetryingTest
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            adminPort: ${free.port.12}
            proxyPort: ${free.port.22}
            rbelBufferSizeInMb: ${tracing.resilience.bufferMb}
            name: Sending proxy
          """,
      skipEnvironmentSetup = true)
  void generateTrafficWhileTheLinkFlapsAndTheProxyCrashes(TigerTestEnvMgr testEnvMgr)
      throws IOException {
    log.info("Workload: {}", replayProperties());

    Files.createDirectories(WORK_DIR);
    adminClient = Unirest.spawnInstance();
    aggregatingAdminPort = freePort();
    final int meshLinkPort = freePort();

    testEnvMgr.setUpEnvironment();
    bootAggregatingProxy();
    meshLink = new MeshLink(meshLinkPort, aggregatingAdminPort, LINK_KB_PER_SECOND * 1024);
    meshLink.raise();
    startReceivingProxy(meshLinkPort);
    startSenders();

    warmUpMesh(testEnvMgr);
    runRounds(
        testEnvMgr,
        () -> {
          restoreMeshLinkWhenTheOutageIsOver();
          randomlyRebootAggregatingProxy();
          randomlyCutMeshLink();
          randomlySilentlyDropMeshLink();
          randomlyKillAggregatingProxy();
        });

    meshLink.removeThrottle();
    meshLink.raise();
    if (aggregatingProxyProcess == null) {
      bootAggregatingProxy();
    }
    awaitConvergence(testEnvMgr, CONVERGENCE_TIMEOUT);
    checkMeshTransmittedEverythingExactlyOnce(testEnvMgr);
  }

  /**
   * The mesh link stops forwarding without closing: sockets stay open, bytes go nowhere, and
   * neither end is told. Nothing here cuts the link, and that is the point - a reconnect repairs
   * this damage completely, so any disruption that produces one hides it. A tracing connection
   * carries no heartbeat on either side, so nothing else notices either.
   *
   * <p>This is expected to fail until the mesh can detect a connection that stopped carrying
   * traffic. What it reports - how much of the traffic went missing - is the measure of how far
   * that is from being true.
   */
  @RetryingTest
  @TigerTest(
      tigerYaml =
          """
          tigerProxy:
            adminPort: ${free.port.12}
            proxyPort: ${free.port.22}
            rbelBufferSizeInMb: ${tracing.resilience.bufferMb}
            name: Sending proxy
          """,
      skipEnvironmentSetup = true)
  void generateTrafficWhileConnectionsAreDroppedSilently(TigerTestEnvMgr testEnvMgr)
      throws IOException {
    log.info("Workload: {}", replayProperties());

    Files.createDirectories(WORK_DIR);
    adminClient = Unirest.spawnInstance();
    aggregatingAdminPort = freePort();
    final int meshLinkPort = freePort();

    testEnvMgr.setUpEnvironment();
    bootAggregatingProxy();
    meshLink = new MeshLink(meshLinkPort, aggregatingAdminPort, LINK_KB_PER_SECOND * 1024);
    meshLink.raise();
    startReceivingProxy(meshLinkPort);
    startSenders();

    warmUpMesh(testEnvMgr);
    runRounds(
        testEnvMgr,
        () -> {
          restoreMeshLinkWhenTheOutageIsOver();
          randomlySilentlyDropMeshLink();
        });

    meshLink.removeThrottle();
    meshLink.raise();
    awaitConvergence(testEnvMgr, CONVERGENCE_TIMEOUT);
    checkMeshTransmittedEverythingExactlyOnce(testEnvMgr);
  }

  private void runRounds(TigerTestEnvMgr testEnvMgr, Runnable disturbTheMesh) {
    final long deadlineNanos =
        TIME_BUDGET_SECONDS > 0
            ? System.nanoTime() + TimeUnit.SECONDS.toNanos(TIME_BUDGET_SECONDS)
            : Long.MAX_VALUE;

    for (int round = 0; round < MASTER_ROUNDS; round++) {
      if (System.nanoTime() > deadlineNanos) {
        log.info("Time budget of {}s exhausted after {} rounds", TIME_BUDGET_SECONDS, round);
        break;
      }
      disturbTheMesh.run();
      barrier(testEnvMgr);
      sendMessages(MESSAGES_PER_ROUND);
      barrier(testEnvMgr);
      log.info(
          "Sent {} requests, sending-proxy has {} msgs, receiving-proxy has {} msgs",
          sentRequestCount(),
          sentMessageCount(testEnvMgr),
          receivedMessageCount());
      pauseBetweenRounds();
    }
  }

  /**
   * A tight send loop fills the sending proxy's ring buffer within seconds, and a workload that
   * outruns its own buffer reports messages as lost that were never meant to be kept. Idling
   * between the rounds is what makes a run that lasts minutes rather than seconds possible.
   */
  private void pauseBetweenRounds() {
    if (PAUSE_BETWEEN_ROUNDS <= 0) {
      return;
    }
    sleepMillis(PAUSE_BETWEEN_ROUNDS);
  }

  @AfterEach
  void shutDownEverythingWeStarted() {
    senders.forEach(sender -> sender.connection.close());
    senders.clear();
    nextSenderOffset = 0;
    if (senderPool != null) {
      senderPool.shutdownNow();
      senderPool = null;
    }
    if (adminClient != null) {
      adminClient.close();
      adminClient = null;
    }
    if (meshLink != null) {
      meshLink.close();
      meshLink = null;
    }
    if (aggregatingProxyProcess != null) {
      aggregatingProxyProcess.destroyForcibly();
      awaitAggregatingProxyExit();
    }
    if (receivingProxy != null) {
      receivingProxy.close();
      receivingProxy = null;
    }
  }

  // ------------------------------------------------------------------ traffic

  private void startSenders() {
    final int proxyPort = readIntegerOptional("free.port.22").orElseThrow();
    for (int index = 0; index < PARALLEL_SENDERS; index++) {
      final UnirestInstance connection = Unirest.spawnInstance();
      connection.config().proxy("127.0.0.1", proxyPort);
      connection.config().followRedirects(false);
      senders.add(new Sender(index, connection));
    }
    senderPool = Executors.newFixedThreadPool(PARALLEL_SENDERS);
  }

  /**
   * Runs traffic through the whole mesh once before the chaos starts. A kill landing while the
   * receiving proxy is still in its initial handshake would leave nothing to observe for the rest
   * of the run. Doubles as the prefill: reaching a big history via rounds is expensive, so the bulk
   * of it is blasted in here.
   */
  private void warmUpMesh(TigerTestEnvMgr testEnvMgr) {
    final int warmUpRequests = Math.max(PREFILL_MESSAGES, senders.size());
    log.info("Warming the mesh up with {} messages...", warmUpRequests);
    sendMessages(warmUpRequests);
    awaitConvergence(testEnvMgr, CONVERGENCE_TIMEOUT);
  }

  /**
   * Every sender walks its own share of the round sequentially - that is what makes its messages a
   * totally ordered sequence which the mesh has to preserve. A round carrying fewer messages than
   * there are senders can only keep that many of them busy, so the starting point rotates: over the
   * run every sender builds a sequence long enough to be reordered, instead of the first
   * messagesPerRound of them carrying the whole workload and the rest sitting on the one message
   * the warm-up gave them.
   */
  private void sendMessages(int count) {
    final int participating = Math.min(count, senders.size());
    final int offset = Math.floorMod(nextSenderOffset, senders.size());
    nextSenderOffset += participating;
    IntStream.range(0, participating)
        .mapToObj(
            slot -> {
              final Sender sender = senders.get((offset + slot) % senders.size());
              final int share = count / participating + (slot < count % participating ? 1 : 0);
              return CompletableFuture.runAsync(() -> sendShareOfRound(sender, share), senderPool);
            })
        .toList()
        .forEach(CompletableFuture::join);
  }

  private void sendShareOfRound(Sender sender, int messages) {
    for (int i = 0; i < messages; i++) {
      sendSingleMessage(sender);
    }
  }

  private void sendSingleMessage(Sender sender) {
    final String url =
        resolvePlaceholders("http://localhost:${free.port.12}")
            + marker(sender.index, sender.nextSequenceNumber);
    if (carriesLargePayload(sender.index, sender.nextSequenceNumber)) {
      sender.connection.post(url).body(LARGE_PAYLOAD).asEmpty();
    } else {
      sender.connection.get(url).asEmpty();
    }
    sender.nextSequenceNumber++;
  }

  private static String marker(int senderIndex, int sequenceNumber) {
    return "/sender" + senderIndex + "/messageNumber" + sequenceNumber;
  }

  /**
   * A body beyond the transmission chunk size is what splits a traced message into several parts on
   * the wire. Derived from the marker rather than drawn randomly, so a replay sends the same
   * shapes.
   */
  private static boolean carriesLargePayload(int senderIndex, int sequenceNumber) {
    return PAYLOAD_BYTES > 0
        && Math.floorMod(senderIndex * 31 + sequenceNumber, 100) < LARGE_MESSAGE_PERCENT;
  }

  // ------------------------------------------------------------- verification

  /**
   * With the barrier enabled no traffic comes in while aggregating and receiving proxy are catching
   * up - which is precisely the race worth testing, hence it is off by default.
   */
  private void barrier(TigerTestEnvMgr testEnvMgr) {
    if (CONVERGENCE_BARRIER && aggregatingProxyProcess != null && meshLinkIsUp()) {
      awaitConvergence(testEnvMgr, BARRIER_TIMEOUT);
    }
  }

  private void awaitConvergence(TigerTestEnvMgr testEnvMgr, Duration timeout) {
    try {
      poll(timeout).until(() -> sentMessageCount(testEnvMgr) == receivedMessageCount());
    } catch (ConditionTimeoutException e) {
      final List<String> missing = missing(markersOf(receivingProxy.getRbelMessagesList()));
      failWithMeshState(
          testEnvMgr,
          "The receiving proxy did not catch up within " + timeout + ".",
          "%d of %d requests have not arrived: %s"
              .formatted(missing.size(), sentRequestCount(), preview(missing)));
    }
  }

  /**
   * The message count alone is passed by a run that lost one message and duplicated another, and by
   * a run that delivered everything in the wrong order.
   */
  private void checkMeshTransmittedEverythingExactlyOnce(TigerTestEnvMgr testEnvMgr) {
    checkSendingProxyRecordedEveryRequest(testEnvMgr);
    checkNothingWasLostOrDuplicated(testEnvMgr);
    checkSenderOrderWasPreserved(testEnvMgr);
  }

  private void checkSendingProxyRecordedEveryRequest(TigerTestEnvMgr testEnvMgr) {
    final List<RbelElement> recorded = sentMessages(testEnvMgr);
    final List<String> missing = missing(markersOf(recorded));
    if (missing.isEmpty() && recorded.size() == sentRequestCount() * 2) {
      return;
    }
    failWithMeshState(
        testEnvMgr,
        "The sending proxy did not record everything it proxied.",
        """
        expected %d messages (%d requests plus their responses), found %d
        requests it never saw (%d): %s\
        """
            .formatted(
                sentRequestCount() * 2,
                sentRequestCount(),
                recorded.size(),
                missing.size(),
                preview(missing)));
  }

  private void checkNothingWasLostOrDuplicated(TigerTestEnvMgr testEnvMgr) {
    final List<String> arrived = markersOf(receivingProxy.getRbelMessagesList());
    final List<String> missing = missing(arrived);
    final List<String> duplicated = duplicatesIn(arrived);
    if (missing.isEmpty() && duplicated.isEmpty()) {
      return;
    }
    failWithMeshState(
        testEnvMgr,
        "The mesh did not deliver every request exactly once.",
        """
        never arrived (%d of %d): %s
        arrived more than once (%d): %s\
        """
            .formatted(
                missing.size(),
                sentRequestCount(),
                preview(missing),
                duplicated.size(),
                preview(duplicated)));
  }

  /**
   * Each sender waits for its response before sending the next request, so its requests reach the
   * sending proxy in a defined order and the mesh must not reshuffle them. Across senders no such
   * order exists, hence the per-sender grouping.
   */
  private void checkSenderOrderWasPreserved(TigerTestEnvMgr testEnvMgr) {
    final Map<Integer, List<Integer>> perSender =
        sequenceNumbersPerSender(receivingProxy.getRbelMessagesList());
    if (perSender.isEmpty()) {
      failWithMeshState(
          testEnvMgr, "Not a single request reached the receiving proxy.", "nothing to compare");
    }
    final Map<Integer, List<Integer>> inversionsPerSender = new TreeMap<>();
    perSender.forEach(
        (senderIndex, sequenceNumbers) -> {
          final List<Integer> inversions = inversionsIn(sequenceNumbers);
          if (!inversions.isEmpty()) {
            inversionsPerSender.put(senderIndex, inversions);
          }
        });
    if (inversionsPerSender.isEmpty()) {
      return;
    }

    final int senderIndex = inversionsPerSender.keySet().iterator().next();
    final List<Integer> sequenceNumbers = perSender.get(senderIndex);
    final int position = inversionsPerSender.get(senderIndex).get(0);
    failWithMeshState(
        testEnvMgr,
        "The receiving proxy reordered the requests of %d of %d senders."
            .formatted(inversionsPerSender.size(), perSender.size()),
        """
        out of order per sender: %s
        sender %d, at position %d of %d:
        %s\
        """
            .formatted(
                inversionsPerSender.entrySet().stream()
                    .map(
                        entry -> "sender %d: %d".formatted(entry.getKey(), entry.getValue().size()))
                    .collect(Collectors.joining(", ")),
                senderIndex,
                position,
                sequenceNumbers.size(),
                windowAround(sequenceNumbers, position)));
  }

  private static List<Integer> inversionsIn(List<Integer> sequenceNumbers) {
    return IntStream.range(0, sequenceNumbers.size() - 1)
        .filter(i -> sequenceNumbers.get(i) > sequenceNumbers.get(i + 1))
        .boxed()
        .toList();
  }

  /** The offending pair in {@code >..<}, with a few of its neighbours for context. */
  private static String windowAround(List<Integer> sequenceNumbers, int position) {
    final int from = Math.max(0, position - 4);
    final int to = Math.min(sequenceNumbers.size(), position + 6);
    return IntStream.range(from, to)
        .mapToObj(
            i ->
                i == position || i == position + 1
                    ? ">" + sequenceNumbers.get(i) + "<"
                    : String.valueOf(sequenceNumbers.get(i)))
        .collect(
            Collectors.joining(
                ", ", from > 0 ? "..., " : "", to < sequenceNumbers.size() ? ", ..." : ""));
  }

  // ----------------------------------------------------------------- reports

  /** Ends every report, so a red run always says where the mesh stood and how to replay it. */
  private void failWithMeshState(TigerTestEnvMgr testEnvMgr, String problem, String detail) {
    fail(
        """
        %s
        %s

        Sending proxy holds %d messages, receiving proxy %d, aggregating proxy is %s.
        Aggregating proxy log: %s
        Rerun this workload: %s\
        """
            .formatted(
                problem,
                detail,
                sentMessageCount(testEnvMgr),
                receivedMessageCount(),
                aggregatingProxyProcess == null
                    ? "down"
                    : "up (pid " + aggregatingProxyProcess.pid() + ")",
                aggregatingProxyLogFile == null ? "never started" : aggregatingProxyLogFile,
                replayProperties()));
  }

  /** For failures before any traffic ran, where the proxy's own log is the only evidence. */
  private void failWithAggregatingProxyLog(String problem) {
    fail(
        """
        %s

        Last lines of %s:
        %s

        Rerun this workload: %s\
        """
            .formatted(
                problem, aggregatingProxyLogFile, tailOfAggregatingProxyLog(), replayProperties()));
  }

  private String tailOfAggregatingProxyLog() {
    try {
      final List<String> lines = Files.readAllLines(aggregatingProxyLogFile);
      return String.join("\n", lines.subList(Math.max(0, lines.size() - 30), lines.size()));
    } catch (IOException | RuntimeException e) {
      return "(unreadable: " + e + ")";
    }
  }

  private static String replayProperties() {
    return KNOBS.entrySet().stream()
        .map(knob -> "-D" + PREFIX + knob.getKey() + "=" + knob.getValue())
        .collect(Collectors.joining(" "));
  }

  /**
   * Markers are collapsed per sender and into ranges, so even a mesh that lost thousands of
   * requests still reports on a single readable line.
   */
  private static String preview(List<String> markers) {
    if (markers.isEmpty()) {
      return "none";
    }
    final Map<Integer, List<Integer>> perSender = new TreeMap<>();
    for (String marker : markers) {
      final Matcher matched = MARKER.matcher(marker);
      if (matched.find()) {
        perSender
            .computeIfAbsent(Integer.valueOf(matched.group(1)), unused -> new ArrayList<>())
            .add(Integer.valueOf(matched.group(2)));
      }
    }
    final String rendered =
        perSender.entrySet().stream()
            .map(entry -> "sender %d: %s".formatted(entry.getKey(), asRanges(entry.getValue())))
            .collect(Collectors.joining("; "));
    return rendered.length() <= PREVIEW_LIMIT
        ? rendered
        : rendered.substring(0, PREVIEW_LIMIT) + "...";
  }

  private static String asRanges(List<Integer> sequenceNumbers) {
    final List<Integer> sorted = sequenceNumbers.stream().sorted().toList();
    final List<String> ranges = new ArrayList<>();
    int start = sorted.get(0);
    int previous = start;
    for (int i = 1; i <= sorted.size(); i++) {
      final int current = i < sorted.size() ? sorted.get(i) : Integer.MAX_VALUE;
      if (current != previous + 1) {
        ranges.add(start == previous ? String.valueOf(start) : start + "-" + previous);
        start = current;
      }
      previous = current;
    }
    return String.join(",", ranges);
  }

  // ------------------------------------------------------------ mesh readouts

  private int sentRequestCount() {
    return senders.stream().mapToInt(sender -> sender.nextSequenceNumber).sum();
  }

  /** Every marker the senders have handed to the mesh so far, in the order they were sent. */
  private List<String> expectedMarkers() {
    return senders.stream()
        .flatMap(
            sender ->
                IntStream.range(0, sender.nextSequenceNumber)
                    .mapToObj(sequenceNumber -> marker(sender.index, sequenceNumber)))
        .toList();
  }

  private List<String> missing(List<String> arrived) {
    final Set<String> present = new HashSet<>(arrived);
    return expectedMarkers().stream().filter(marker -> !present.contains(marker)).toList();
  }

  private static List<String> duplicatesIn(List<String> markers) {
    final Set<String> seen = new HashSet<>();
    return markers.stream().filter(marker -> !seen.add(marker)).distinct().toList();
  }

  private Map<Integer, List<Integer>> sequenceNumbersPerSender(List<RbelElement> messages) {
    return requestMarkersOf(messages)
        .collect(
            Collectors.groupingBy(
                marker -> Integer.valueOf(marker.group(1)),
                TreeMap::new,
                Collectors.mapping(
                    marker -> Integer.valueOf(marker.group(2)), Collectors.toList())));
  }

  private List<String> markersOf(List<RbelElement> messages) {
    return requestMarkersOf(messages).map(Matcher::group).toList();
  }

  /** Only requests carry a marker, so this also filters the responses out. */
  private Stream<Matcher> requestMarkersOf(List<RbelElement> messages) {
    return messages.stream()
        .map(RbelElement::getRawStringContent)
        .filter(Objects::nonNull)
        .map(content -> content.lines().findFirst())
        .flatMap(Optional::stream)
        .map(MARKER::matcher)
        .filter(Matcher::find);
  }

  private static List<RbelElement> sentMessages(TigerTestEnvMgr testEnvMgr) {
    return testEnvMgr.getLocalTigerProxyOrFail().getRbelMessagesList();
  }

  private static int sentMessageCount(TigerTestEnvMgr testEnvMgr) {
    return testEnvMgr.getLocalTigerProxyOrFail().getMessageHistory().size();
  }

  private int receivedMessageCount() {
    return receivingProxy.getRbelLogger().getMessages().size();
  }

  // ----------------------------------------------------------------- proxies

  private void startReceivingProxy(int meshPort) {
    receivingProxy =
        new TigerProxy(
            TigerProxyConfiguration.builder()
                .adminPort(readIntegerOptional("free.port.10").orElseThrow())
                .proxyPort(readIntegerOptional("free.port.20").orElseThrow())
                .trafficEndpoints(List.of("http://localhost:" + meshPort))
                .downloadInitialTrafficFromEndpoints(true)
                .connectionTimeoutInSeconds(100)
                .skipTrafficEndpointsSubscription(false)
                .name("Receiving proxy")
                .build());
    if (PARSE_DELAY_MILLIS > 0) {
      slowDownParsingOf(receivingProxy);
    }
    receivingProxy.subscribeToTrafficEndpoints();
    log.info("Started Receiving Proxy");
  }

  /**
   * Stands in for a runner that cannot parse as fast as the mesh delivers. Every other pressure
   * this test applies sits on the wire; this one sits behind it, where the queues and the assembly
   * deadlines are - a message that is merely slow is indistinguishable from one that will never be
   * whole.
   *
   * <p>The plugin has to stay anonymous. {@code RbelConverterInitializer} scans {@code de.gematik}
   * for every {@link RbelConverterPlugin} and instantiates what it finds, so a named one here would
   * be registered in every proxy of this JVM - including the sending one, which is the side that
   * must stay fast. Anonymous classes are the one shape that scan skips.
   */
  private void slowDownParsingOf(TigerProxy proxy) {
    proxy
        .getRbelLogger()
        .getRbelConverter()
        .addConverter(
            new RbelConverterPlugin() {
              @Override
              public RbelConversionPhase getPhase() {
                return RbelConversionPhase.PREPARATION;
              }

              @Override
              public void consumeElement(
                  RbelElement rbelElement, RbelConversionExecutor converter) {
                sleepMillis(PARSE_DELAY_MILLIS);
              }
            });
  }

  private static void sleepMillis(long millis) {
    try {
      TimeUnit.MILLISECONDS.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while slowing the mesh down", e);
    }
  }

  private void bootAggregatingProxy() {
    awaitAdminPortIsFree();
    aggregatingProxyLogFile =
        WORK_DIR
            .resolve("aggregating-proxy-" + ++aggregatingProxyGeneration + ".log")
            .toAbsolutePath();
    log.info("Starting Aggregating Proxy, logging to {}...", aggregatingProxyLogFile);
    try {
      aggregatingProxyProcess =
          new ProcessBuilder(aggregatingProxyCommand())
              .directory(WORK_DIR.toFile())
              .redirectErrorStream(true)
              .redirectOutput(aggregatingProxyLogFile.toFile())
              .start();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not start the aggregating proxy", e);
    }
    awaitAggregatingProxyIsServing();
    log.info("Started Aggregating Proxy (pid {})", aggregatingProxyProcess.pid());
  }

  private void awaitAdminPortIsFree() {
    try {
      poll(PROXY_BOOT_TIMEOUT).until(() -> isPortFree(aggregatingAdminPort));
    } catch (ConditionTimeoutException e) {
      fail(
          """
          Admin port %d was still taken %s after the previous aggregating proxy exited, so the \
          next one cannot bind it.

          Rerun this workload: %s\
          """
              .formatted(aggregatingAdminPort, PROXY_BOOT_TIMEOUT, replayProperties()));
    }
  }

  private void awaitAggregatingProxyIsServing() {
    final String versionUrl = "http://localhost:" + aggregatingAdminPort + "/webui/version";
    try {
      poll(PROXY_BOOT_TIMEOUT)
          .until(
              () -> {
                if (!aggregatingProxyProcess.isAlive()) {
                  failWithAggregatingProxyLog(
                      "The aggregating proxy exited with code %d while starting up."
                          .formatted(aggregatingProxyProcess.exitValue()));
                }
                try {
                  return adminClient.get(versionUrl).asEmpty().getStatus() == 200;
                } catch (RuntimeException e) {
                  return false;
                }
              });
    } catch (ConditionTimeoutException e) {
      failWithAggregatingProxyLog(
          "The aggregating proxy was still not answering on %s after %s."
              .formatted(versionUrl, PROXY_BOOT_TIMEOUT));
    }
  }

  private List<String> aggregatingProxyCommand() {
    final CfgStandaloneProxy standaloneCfg = new CfgStandaloneProxy();
    standaloneCfg.setTigerProxy(
        TigerProxyConfiguration.builder()
            .adminPort(aggregatingAdminPort)
            .trafficEndpoints(List.of(resolvePlaceholders("http://localhost:${free.port.12}")))
            .downloadInitialTrafficFromEndpoints(true)
            .rbelBufferSizeInMb(BUFFER_MB)
            .activateRbelParsing(false)
            .connectionTimeoutInSeconds(100)
            .name("Aggregating proxy")
            .build());

    final List<String> command =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                TigerProxyApplication.class.getName()));
    TigerSerializationUtil.toMap(standaloneCfg)
        .forEach((key, value) -> command.add("--" + key + "=" + value));
    return command;
  }

  private boolean meshLinkIsUp() {
    return meshLink == null || meshLink.isUp();
  }

  /**
   * An outage is measured in time, not in rounds: how much traffic a round carries is a knob, so
   * counting rounds would make the length of an outage depend on the shape of the workload.
   */
  private void randomlyCutMeshLink() {
    if (meshLink.isUp() && random.nextInt(CUT_ONE_IN) == 0) {
      log.info("Cutting the mesh link for {}ms...", LINK_DOWN_MILLIS);
      meshLink.cut();
      meshLinkRestoreAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LINK_DOWN_MILLIS);
    }
  }

  private void restoreMeshLinkWhenTheOutageIsOver() {
    if (!meshLink.isUp() && System.nanoTime() > meshLinkRestoreAtNanos) {
      log.info("Restoring the mesh link...");
      meshLink.raise();
    }
    if (meshLink.isSilentlyDropping() && System.nanoTime() > meshLinkResumeAtNanos) {
      log.info("Resuming the mesh link after the silent drop...");
      meshLink.resume();
    }
  }

  /**
   * The counterpart to a cut: no close, no error, nothing for either end to react to. Whatever the
   * mesh loses here it has to notice by itself, because nothing will tell it.
   */
  private void randomlySilentlyDropMeshLink() {
    if (SILENT_DROP_ONE_IN <= 0 || meshLink.isSilentlyDropping() || !meshLink.isUp()) {
      return;
    }
    if (random.nextInt(SILENT_DROP_ONE_IN) == 0) {
      log.info("Dropping the mesh link silently for {}ms...", SILENT_DROP_MILLIS);
      meshLink.dropSilently();
      meshLinkResumeAtNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SILENT_DROP_MILLIS);
    }
  }

  /**
   * A TCP forwarder in front of the aggregating proxy's admin port, standing in for the ingress the
   * mesh runs through in a cluster. Cutting it drops every open connection and refuses new ones -
   * what a lost route looks like from both ends. Unlike a kill it leaves the aggregating proxy and,
   * more importantly, its history alive.
   */
  private static final class MeshLink implements AutoCloseable {

    private final int listenPort;
    private final int targetPort;
    private final ExecutorService connections = Executors.newCachedThreadPool();
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private volatile ServerSocket listener;
    private volatile int bytesPerSecond;
    private volatile boolean silentlyDropping;

    private MeshLink(int listenPort, int targetPort, int bytesPerSecond) {
      this.listenPort = listenPort;
      this.targetPort = targetPort;
      this.bytesPerSecond = bytesPerSecond;
    }

    /**
     * Congestion is a phase of the run, not the verdict on it - the final convergence has to be
     * given a link that can actually carry the backlog, or every run fails for being slow rather
     * than for losing anything.
     */
    private void removeThrottle() {
      bytesPerSecond = 0;
      silentlyDropping = false;
    }

    private void dropSilently() {
      silentlyDropping = true;
    }

    private void resume() {
      silentlyDropping = false;
    }

    private boolean isSilentlyDropping() {
      return silentlyDropping;
    }

    private boolean isUp() {
      return listener != null;
    }

    private void raise() {
      if (listener != null) {
        return;
      }
      try {
        final ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("127.0.0.1", listenPort));
        listener = socket;
        connections.submit(() -> acceptUntilCut(socket));
      } catch (IOException e) {
        throw new UncheckedIOException("Could not raise the mesh link on port " + listenPort, e);
      }
    }

    private void cut() {
      closeQuietly(listener);
      listener = null;
      openSockets.forEach(MeshLink::closeQuietly);
      openSockets.clear();
    }

    private void acceptUntilCut(ServerSocket socket) {
      while (!socket.isClosed()) {
        final Socket incoming;
        try {
          incoming = socket.accept();
        } catch (IOException e) {
          return; // only the listener going away ends this loop, and that is what cut() does
        }
        forward(incoming);
      }
    }

    /**
     * A refused outbound connect means the aggregating proxy is down, not that the link is gone -
     * the link has to survive that, or it would stay dead for the rest of the run while {@link
     * #isUp()} keeps claiming otherwise.
     */
    private void forward(Socket incoming) {
      final Socket outgoing;
      try {
        outgoing = new Socket("127.0.0.1", targetPort);
      } catch (IOException e) {
        closeQuietly(incoming);
        return;
      }
      openSockets.add(incoming);
      openSockets.add(outgoing);
      connections.submit(() -> pump(incoming, outgoing));
      connections.submit(() -> pump(outgoing, incoming));
    }

    /** A broken pipe is the whole point of this class, so nothing here is worth reporting. */
    private void pump(Socket from, Socket to) {
      try (from;
          to) {
        copy(from.getInputStream(), to.getOutputStream());
      } catch (IOException e) {
        log.trace("Mesh link pump ended: {}", e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        openSockets.remove(from);
        openSockets.remove(to);
      }
    }

    /**
     * Three ways for a link to misbehave, and they fail differently. A dead one stops and says so,
     * so both ends notice. A slow one lets the sender keep producing into a buffer nobody drains,
     * which is what reaches the pending send buffer. A silently dropped one keeps reading and
     * writes nowhere - the sockets stay open, so neither end has anything to notice, and nothing
     * triggers a reconnect.
     */
    private void copy(InputStream from, OutputStream to) throws IOException, InterruptedException {
      final byte[] buffer = new byte[8192];
      int read;
      while ((read = from.read(buffer)) >= 0) {
        if (!silentlyDropping) {
          to.write(buffer, 0, read);
          to.flush();
        }
        final int rate = bytesPerSecond;
        if (rate > 0) {
          TimeUnit.MILLISECONDS.sleep(Math.max(1, (long) read * 1000 / rate));
        }
      }
    }

    @Override
    public void close() {
      cut();
      connections.shutdownNow();
    }

    private static void closeQuietly(java.io.Closeable closeable) {
      if (closeable == null) {
        return;
      }
      try {
        closeable.close();
      } catch (IOException e) {
        log.trace("Could not close {}: {}", closeable, e.getMessage());
      }
    }
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not find a free port", e);
    }
  }

  private void randomlyRebootAggregatingProxy() {
    if (aggregatingProxyProcess == null && random.nextInt(REBOOT_ONE_IN) == 0) {
      bootAggregatingProxy();
    }
  }

  private void randomlyKillAggregatingProxy() {
    if (aggregatingProxyProcess != null && random.nextInt(CRASH_ONE_IN) == 0) {
      killAggregatingProxy(random.nextInt(100) < GRACEFUL_KILL_PERCENT);
    }
  }

  /**
   * SIGKILL leaves the mesh to notice the broken socket on its own; SIGTERM lets Spring run its
   * shutdown hook and close the sessions properly.
   */
  private void killAggregatingProxy(boolean graceful) {
    log.info(
        "{} aggregating proxy (pid {})...",
        graceful ? "Stopping" : "Killing",
        aggregatingProxyProcess.pid());
    if (graceful) {
      aggregatingProxyProcess.destroy();
    } else {
      aggregatingProxyProcess.destroyForcibly();
    }
    awaitAggregatingProxyExit();
  }

  private void awaitAggregatingProxyExit() {
    try {
      if (!aggregatingProxyProcess.waitFor(PROXY_EXIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        aggregatingProxyProcess.destroyForcibly().waitFor();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the aggregating proxy", e);
    } finally {
      aggregatingProxyProcess = null;
    }
  }

  private static boolean isPortFree(int port) {
    try (ServerSocket ignored = new ServerSocket(port)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static ConditionFactory poll(Duration timeout) {
    return await().atMost(timeout).pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(20));
  }
}
