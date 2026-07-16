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

import static org.assertj.core.api.Assertions.assertThat;

import de.gematik.test.tiger.common.data.config.tigerproxy.TigerProxyConfiguration;
import de.gematik.test.tiger.lifecycle.ILifecycleManager;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.events.AfterServerStartEvent;
import de.gematik.test.tiger.testenvmgr.junit.TigerTest;
import de.gematik.test.tiger.testenvmgr.servers.AbstractTigerServer;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerStatus;
import de.gematik.test.tiger.testenvmgr.servers.TigerServerType;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for an indefinite startup hang caused by synchronous {@link
 * de.gematik.test.tiger.testenvmgr.events.AfterServerStartEvent} delivery.
 *
 * <p><strong>Note:</strong> this is <em>not</em> a classic deadlock (no circular lock dependency).
 * It is an <strong>indefinite block</strong>: the setup thread waits for a {@link
 * java.util.concurrent.CompletableFuture} that a worker thread never completes, because the worker
 * is stuck executing a subscriber callback that never returns. Nobody holds a lock the other needs
 * — the worker simply never finishes.
 *
 * <h2>Root cause (before fix)</h2>
 *
 * <pre>
 * ──────────────────────────────────────────────────────────────────────────────────
 *  setup thread                          worker thread (cachedExecutor)
 * ──────────────────────────────────────────────────────────────────────────────────
 *  setUpEnvironment()
 *    └─ startAllServersInParallel()
 *         └─ CompletableFuture
 *              .allOf(...).get()  ◄── waits for       server.start()
 *                                    all futures        └─ performStartup()    [OK]
 *                                    to complete        └─ setStatus(RUNNING)  [OK]
 *                                                       └─ eventBus.publish(   ← synchronous!
 *                                                            AfterServerStartEvent)
 *                                                              └─ slowSubscriber()
 *                                                                   └─ never returns
 *                                                       ← future.complete() never reached
 *         ← .get() waits forever
 *    ← setup never finishes        "X READY" is the last log line seen
 * ──────────────────────────────────────────────────────────────────────────────────
 *  Result: the JVM stays alive (WebSocket heartbeats continue) but test execution
 *  never begins. Observed intermittently in production as a silent hang right after
 *  the last "READY" log entry.
 * </pre>
 *
 * <h2>Fix</h2>
 *
 * {@code AfterServerStartEvent} is now published on a separate executor thread so a slow or
 * blocking subscriber cannot prevent the server's {@code CompletableFuture} from completing, and
 * therefore cannot stall {@code setUpEnvironment()}.
 *
 * <pre>
 * ──────────────────────────────────────────────────────────────────────────────────
 *  setup thread              worker thread              subscriber thread
 * ──────────────────────────────────────────────────────────────────────────────────
 *  setUpEnvironment()
 *    └─ startAllServersInParallel()
 *         └─ allOf(...).get()  server.start()
 *                                └─ setStatus(RUNNING)
 *                                └─ executor.submit( ──────────────────► slowSubscriber()
 *                                     publish(...))    [fire & forget]    (background, decoupled)
 *                                └─ future.complete(null) ← does NOT wait for subscriber
 *              ← future complete
 *    ← .get() returns
 *    └─ afterServersStart() callback
 *  ← setup finishes normally
 * ──────────────────────────────────────────────────────────────────────────────────
 * </pre>
 */
class TestAfterServerStartSubscriberBlocking {

  @TigerServerType("instantServer")
  @SuppressWarnings("unused")
  public static class InstantServer extends AbstractTigerServer {
    public InstantServer(String serverName, CfgServer config, TigerTestEnvMgr envMgr) {
      super(serverName, config, envMgr);
    }

    @Override
    public void performStartup() {
      // no-op startup: the server reaches READY quickly
    }

    @Override
    public void shutdown() {
      // no-op
    }
  }

  /**
   *
   *
   * <h3>Diagram: Block after parallel startup phase</h3>
   *
   * <pre>
   * setup thread                                 lifecycle callback thread
   * ─────────────────────────────────────────────────────────────────────────
   * setUpEnvironment()
   *   ├─ startAllServersInParallel()
   *   │   ├─ quickA -> RUNNING
   *   │   └─ quickB -> RUNNING
   *   ├─ allOf(...).get() returns      (parallel server startup is complete)
   *   ├─ afterServersStart() ----------> custom callback blocks on latch
   *   │                                   (holds setup progression)
   *   ├─ subscribeToTrafficEndpoints()   [not reached while blocked]
   *   └─ finished setup                  [not reached while blocked]
   *
   * unblock latch ---> callback returns ---> setup continues ---> setup finishes
   * </pre>
   *
   * <p>This is not a lock deadlock. It is an intentional blocking point to prove the exact
   * transition boundary: all servers are already RUNNING, but setup is still paused in the
   * afterServersStart callback.
   */
  @Test
  @TigerTest(
      tigerYaml =
          """
          localProxyActive: false
          servers:
            quickA:
              type: instantServer
              hostname: quickA
            quickB:
              type: instantServer
              hostname: quickB
          """,
      skipEnvironmentSetup = true)
  void testBlockingAfterServersStartCanFreezeEnvironmentAfterParallelStream(TigerTestEnvMgr envMgr)
      throws Exception {
    CountDownLatch afterServersStartEntered = new CountDownLatch(1);
    CountDownLatch afterServersStartRelease = new CountDownLatch(1);
    AtomicBoolean beforeServersStartCalled = new AtomicBoolean(false);

    envMgr.setLifecycleManager(
        new ILifecycleManager() {
          @Override
          public void beforeReadConfiguration() {
            // no-op for this focused test
          }

          @Override
          public void afterReadConfiguration() {
            // no-op for this focused test
          }

          @Override
          public void beforeServersStart() {
            beforeServersStartCalled.set(true);
          }

          @Override
          public void afterServersStart() {
            afterServersStartEntered.countDown();
            awaitOrFail(afterServersStartRelease);
          }

          @Override
          public void beforeLocalTigerProxyStart(
              @NotNull TigerProxyConfiguration localTigerProxyConfiguration) {
            // no-op for this focused test
          }

          @Override
          public void afterLocalTigerProxyStart(
              @NotNull TigerProxyConfiguration localTigerProxyConfiguration) {
            // no-op for this focused test
          }
        });

    var exec = Executors.newSingleThreadExecutor();
    try {
      CompletableFuture<Void> setupFuture =
          CompletableFuture.runAsync(envMgr::setUpEnvironment, exec);

      assertThat(afterServersStartEntered.await(5, TimeUnit.SECONDS))
          .as("afterServersStart should be reached")
          .isTrue();
      assertThat(beforeServersStartCalled.get())
          .as("beforeServersStart should have been called")
          .isTrue();

      // We are now blocked in TigerTestEnvMgr afterServersStart().
      assertThat(envMgr.getServers().get("quickA").getStatus())
          .as("quickA should already be RUNNING")
          .isEqualTo(TigerServerStatus.RUNNING);
      assertThat(envMgr.getServers().get("quickB").getStatus())
          .as("quickB should already be RUNNING")
          .isEqualTo(TigerServerStatus.RUNNING);
      assertThat(setupFuture.isDone())
          .as("setUpEnvironment should still be blocked in afterServersStart")
          .isFalse();

      afterServersStartRelease.countDown();
      setupFuture.get(10, TimeUnit.SECONDS);
      assertThat(setupFuture.isDone()).isTrue();
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  @TigerTest(
      tigerYaml =
          """
          localProxyActive: false
          servers:
            quick:
              type: instantServer
              hostname: quick
          """,
      skipEnvironmentSetup = true)
  void testSetupShouldNotBlockOnAfterServerStartSubscriber(TigerTestEnvMgr envMgr)
      throws Exception {
    CountDownLatch subscriberEntered = new CountDownLatch(1);
    CountDownLatch subscriberRelease = new CountDownLatch(1);

    envMgr
        .getLifecycleEventBus()
        .subscribe(
            AfterServerStartEvent.class,
            event -> {
              subscriberEntered.countDown();
              awaitOrFail(subscriberRelease);
            });

    var exec = Executors.newSingleThreadExecutor();
    CompletableFuture<Void> setupFuture = null;
    try {
      setupFuture = CompletableFuture.runAsync(envMgr::setUpEnvironment, exec);

      assertThat(subscriberEntered.await(5, TimeUnit.SECONDS))
          .as("sanity check: blocking subscriber should be entered")
          .isTrue();

      assertThat(setupFuture)
          .as("regression: setup should complete even if AfterServerStart subscriber blocks")
          .succeedsWithin(Duration.ofMillis(500));
    } finally {
      subscriberRelease.countDown();
      if (setupFuture != null) {
        setupFuture.get(10, TimeUnit.SECONDS);
      }
      exec.shutdownNow();
    }
  }

  private static void awaitOrFail(CountDownLatch latch) {
    try {
      boolean ok = latch.await(10, TimeUnit.SECONDS);
      if (!ok) {
        throw new IllegalStateException("Timeout while waiting for latch release");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for latch release", e);
    }
  }
}
