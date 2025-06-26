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

import de.gematik.test.tiger.lib.TigerDirector;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
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

  public TestEnvironmentMojo() {
    super();
  }

  @Override
  public void execute() {
    if (skip) {
      getLog().info("Skipping");
      return;
    }

    val classLoader = buildUpClassLoader();
    Future testEnvFuture = buildTestEnvFutureAndRun(classLoader);

    try {
      testEnvFuture.get();
      if (TigerDirector.getTigerTestEnvMgr() != null) {
        TigerDirector.getTigerTestEnvMgr().shutDown();
      }
    } catch (InterruptedException | ExecutionException cte) {
      getLog().info("Tiger Testenvironment TIMEOUT reached, shutting down...");
      if (TigerDirector.getTigerTestEnvMgr() != null) {
        TigerDirector.getTigerTestEnvMgr().shutDown();
      }
    }
    getLog().info("Tiger standalone test environment is shut down!");
  }

  private Future<Void> buildTestEnvFutureAndRun(ClassLoader classLoader) {
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
            TigerDirector.startStandaloneTestEnvironment();
            getLog().info("Tiger standalone test environment is setup!");
          } catch (Exception e) {
            getLog().error("Failed to start application", e);
            throw e;
          }
          return null;
        };

    ExecutorService executor = Executors.newSingleThreadExecutor();
    return executor.submit(task);
  }

  @SneakyThrows
  private ClassLoader buildUpClassLoader() {
    List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();

    List<URL> urls =
        runtimeClasspathElements.stream()
            .map(
                path -> {
                  try {
                    return new File(path).toURI().toURL();
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toList();

    project.getArtifacts().stream()
        .filter(
            artifact -> {
              String scope = artifact.getScope();
              return "compile".equals(scope) || "runtime".equals(scope);
            })
        .map(Artifact::getFile)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

    return new URLClassLoader(urls.toArray(new URL[urls.size()]));
  }
}
