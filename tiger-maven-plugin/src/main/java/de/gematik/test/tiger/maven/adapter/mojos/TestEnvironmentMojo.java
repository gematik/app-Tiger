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
package de.gematik.test.tiger.maven.adapter.mojos;

import static de.gematik.test.tiger.common.util.FunctionWithCheckedException.unchecked;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.val;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

/**
 * This plugin allows to start up the Tiger test environment configured in a specific tiger yaml
 * file in the pre-integration-test phase. To trigger use the "setup-testenv" goal. For more details
 * please refer to the README.adoc file in the project root.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Mojo(
    name = "setup-testenv",
    defaultPhase = LifecyclePhase.INITIALIZE,
    requiresDependencyResolution = ResolutionScope.RUNTIME)
@Execute(phase = LifecyclePhase.PROCESS_CLASSES)
public class TestEnvironmentMojo extends AbstractMojo {

  public static final String ORG_SPRINGFRAMEWORK_BOOT_LOGGING_LOGGING_SYSTEM =
      "org.springframework.boot.logging.LoggingSystem";

  /** Skip running this plugin. Default is false. */
  @Parameter private boolean skip = false;

  /** Timespan to keep the test environment up and running in seconds */
  @Parameter(defaultValue = "86400")
  private long autoShutdownAfterSeconds = Duration.ofDays(1).getSeconds();

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Setter private Supplier<ClassLoader> classLoaderBuilder;

  public TestEnvironmentMojo() {
    super();
    classLoaderBuilder = this::buildUpClassLoader;
  }

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Skipping");
      return;
    }

    val classLoader = classLoaderBuilder.get();
    AtomicReference<ExecutorService> executorRef = new AtomicReference<>();
    Future<?> testEnvFuture = buildTestEnvFutureAndRun(classLoader, executorRef);

    try {
      testEnvFuture.get(autoShutdownAfterSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException timeout) {
      getLog()
          .info(
              "Auto shutdown timeout reached after "
                  + autoShutdownAfterSeconds
                  + " seconds. Shutting down test environment.");
    } catch (InterruptedException cte) {
      Thread.currentThread().interrupt();
      throw new MojoExecutionException("Error while running Tiger Testenvironment", cte);
    } catch (ExecutionException cte) {
      Throwable cause = cte.getCause() != null ? cte.getCause() : cte;
      getLog().error("Tiger test environment startup failed: " + cause.getMessage(), cause);
      throw new MojoExecutionException("Error while running Tiger Testenvironment", cause);
    } finally {
      if (isTigerDirectorInitialized(classLoader)) {
        Object tigerTestEnvMgr = invokeTigerDirector(classLoader, "getTigerTestEnvMgr");
        if (tigerTestEnvMgr != null) {
          try {
            tigerTestEnvMgr.getClass().getMethod("shutDown").invoke(tigerTestEnvMgr);
          } catch (ReflectiveOperationException e) {
            getLog()
                .warn(
                    "Unable to invoke "
                        + tigerTestEnvMgr.getClass().getName()
                        + ".shutDown during cleanup",
                    e);
          }
          getLog().info("Tiger standalone test environment is shut down!");
        }
      }
      ExecutorService executor = executorRef.get();
      if (executor != null) {
        executor.shutdownNow();
      }
    }
  }

  private Future<Void> buildTestEnvFutureAndRun(
      ClassLoader classLoader, AtomicReference<ExecutorService> executorRef) {
    Callable<Void> task =
        () -> {
          Thread.currentThread().setContextClassLoader(classLoader);
          try {
            if (System.getProperty(ORG_SPRINGFRAMEWORK_BOOT_LOGGING_LOGGING_SYSTEM) == null) {
              System.setProperty(ORG_SPRINGFRAMEWORK_BOOT_LOGGING_LOGGING_SYSTEM, "none");
            } else {
              if (!System.getProperty(ORG_SPRINGFRAMEWORK_BOOT_LOGGING_LOGGING_SYSTEM)
                  .equals("none")) {
                getLog()
                    .warn(
                        "Spring Boot Logging System property '"
                            + ORG_SPRINGFRAMEWORK_BOOT_LOGGING_LOGGING_SYSTEM
                            + "' is set so we will not overwrite it to 'none', this may cause"
                            + " startup failure with the maven logging system!");
              }
            }
            invokeTigerDirector(classLoader, "startStandaloneTestEnvironment");
            getLog().info("Tiger standalone test environment is setup!");
            // Keep this worker alive so setup-testenv remains blocking until timeout/interrupt.
            new CountDownLatch(1).await();
          } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
          } catch (Exception e) {
            getLog().error("Failed to start application", e);
            throw e;
          }
          return null;
        };

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executorRef.set(executor);
    return executor.submit(task);
  }

  public ClassLoader buildUpClassLoader() {
    val mergedUrls = new LinkedHashSet<URL>();
    mergedUrls.addAll(Arrays.asList(getPluginClasspathUrls()));
    mergedUrls.addAll(Arrays.asList(getProjectClasspathUrls()));

    return new URLClassLoader(mergedUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
  }

  URL[] getPluginClasspathUrls() {
    ClassLoader currentClassLoader = getClass().getClassLoader();
    if (currentClassLoader instanceof URLClassLoader urlClassLoader) {
      return urlClassLoader.getURLs();
    }
    return new URL[0];
  }

  public URL[] getProjectClasspathUrls() {
    List<String> runtimeClasspathElements;
    try {
      runtimeClasspathElements = project.getRuntimeClasspathElements();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return runtimeClasspathElements.stream()
        .map(unchecked(path -> new File(path).toURI().toURL()))
        .toArray(URL[]::new);
  }

  private Object invokeTigerDirector(ClassLoader classLoader, String methodName) {
    try {
      Class<?> tigerDirectorClass =
          Class.forName("de.gematik.test.tiger.lib.TigerDirector", true, classLoader);
      return tigerDirectorClass.getMethod(methodName).invoke(null);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Unable to invoke TigerDirector." + methodName, e);
    }
  }

  private boolean isTigerDirectorInitialized(ClassLoader classLoader) {
    Object initialized = invokeTigerDirector(classLoader, "isInitialized");
    return initialized instanceof Boolean b && b;
  }
}
