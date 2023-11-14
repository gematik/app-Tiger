/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.maven.adapter.mojos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.org.webcompere.systemstubs.SystemStubs.tapSystemOut;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.lib.TigerDirector;
import io.restassured.RestAssured;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestEnvironmentMojoTest {

  ExecutorService executor;

  TestEnvironmentMojo mojo;

  @BeforeEach
  public void clearTigerGlobalConfig() {
    TigerGlobalConfiguration.reset();
    executor = Executors.newSingleThreadExecutor();
    TigerDirector.testUninitialize();
    mojo = new TestEnvironmentMojo();
  }

  @AfterEach
  public void clearSystemProperty() {
    System.clearProperty("tiger.testenv.cfgfile");
    executor.shutdown();
    TigerDirector.testUninitialize();
  }

  @Test
  void testLocalJarEnv_OK() {
    System.setProperty(
        "tiger.testenv.cfgfile",
        "src/test/resources/de/gematik/test/tiger/maven/adapter/mojos/testExternalLocalJar.yaml");

    Future<Boolean> future =
        executor.submit(
            () -> {
              mojo.execute();
              return true;
            });

    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              try {
                return mojo.isRunning()
                    && RestAssured.get(
                                "http://127.0.0.1:"
                                    + TigerGlobalConfiguration.readString("free.port.0"))
                            .getStatusCode()
                        == 200;
              } catch (Exception rte) {
                return false;
              }
            });
    mojo.abort();
    await().pollInterval(100, TimeUnit.MILLISECONDS).until(() -> future.isDone());
  }

  @Test
  @SneakyThrows
  void testFlagsSet_OK() {
    System.setProperty(
        "tiger.testenv.cfgfile",
        "src/test/resources/de/gematik/test/tiger/maven/adapter/mojos/testExternalLocalJarWithFlags.yaml");
    AtomicReference<String> systemOut = new AtomicReference<>();
    Future<Boolean> future =
        executor.submit(
            () -> {
              String sysout =
                  tapSystemOut(
                      () -> {
                        mojo.execute();
                      });
              systemOut.set(sysout);
              System.out.println(sysout);
              return true;
            });

    await()
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .until(
            () -> {
              try {
                return mojo.isRunning()
                    && RestAssured.get(
                                "http://127.0.0.1:"
                                    + TigerGlobalConfiguration.readString("free.port.0"))
                            .getStatusCode()
                        == 200;
              } catch (Exception rte) {
                return false;
              }
            });

    mojo.abort();
    await().pollInterval(100, TimeUnit.MILLISECONDS).until(() -> future.isDone());

    assertThat(systemOut.get())
        .contains(
            "Starting local Tiger Proxy in standalone mode is not supported, deactivating the flag"
                + " in config")
        .contains(
            "Starting WorkflowUI in standalone mode is not supported, deactivating the flag in"
                + " config");
  }

  @Test
  @SneakyThrows
  void testLocalJarEnvSkip_OK() {
    mojo.setSkip(true);

    Future<Boolean> future =
        executor.submit(
            () -> {
              mojo.execute();
              return true;
            });

    assertThat(mojo.isRunning()).isFalse();
    await()
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .atMost(2, TimeUnit.SECONDS)
        .until(future::isDone);
    assertThat(mojo.isRunning()).isFalse();
    assertThat(future.get()).isTrue();
  }

  @Test
  @SneakyThrows
  void testLocalJarEnvTimeout_OK() {
    mojo.setAutoShutdownAfterSeconds(1);
    Future<Boolean> future =
        executor.submit(
            () -> {
              mojo.execute();
              return true;
            });

    await()
        .pollInterval(100, TimeUnit.MILLISECONDS)
        .atMost(25, TimeUnit.SECONDS)
        .until(mojo::isRunning);
    await()
        .pollInterval(200, TimeUnit.MILLISECONDS)
        .atMost(25, TimeUnit.SECONDS)
        .until(() -> future.isDone() && !mojo.isRunning());
  }
}
