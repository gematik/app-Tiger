/*
 * Copyright (c) 2024 gematik GmbH
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

package de.gematik.test.tiger.testenvmgr.servers;

import static de.gematik.rbellogger.util.GlobalServerMap.updateGlobalServerMap;
import static java.time.LocalDateTime.now;

import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.CfgExternalJarOptions;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerStreamLogFeeder;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Builder;
import org.slf4j.event.Level;

@TigerServerType("externalJar")
public class ExternalJarServer extends AbstractExternalTigerServer {

  public static final String LOCAL = "local:";
  private final AtomicReference<Process> processReference = new AtomicReference<>();
  private File jarFile;
  private LocalDateTime processStartTime;

  @Builder
  public ExternalJarServer(
      TigerTestEnvMgr tigerTestEnvMgr, String serverId, CfgServer configuration) {
    super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
  }

  @Override
  public void assertThatConfigurationIsCorrect() {
    super.assertThatConfigurationIsCorrect();

    assertCfgPropertySet(getConfiguration(), "source");

    // defaulting work dir to temp folder on system if not set in config
    String folder;
    if (getConfiguration().getExternalJarOptions() == null) {
      folder = new File(".").getAbsolutePath();
      log.info(
          "Defaulting to current working folder '{}' as working directory for server {}",
          folder,
          getServerId());
      getConfiguration().setExternalJarOptions(new CfgExternalJarOptions());
    } else {
      folder = getConfiguration().getExternalJarOptions().getWorkingDir();
      if (folder == null) {
        if (getConfiguration().getSource().get(0).startsWith(LOCAL)) {
          final String jarPath = getConfiguration().getSource().get(0).split(LOCAL)[1];
          folder = Paths.get(jarPath).toAbsolutePath().getParent().toString();
          if (jarPath.contains("/")) {
            getConfiguration()
                .getSource()
                .add(0, LOCAL + jarPath.substring(jarPath.lastIndexOf('/')));
          } else {
            getConfiguration().getSource().add(0, LOCAL + jarPath);
          }
          log.info(
              "Defaulting to parent folder '{}' as working directory for server {}",
              folder,
              getServerId());
        } else {
          folder =
              Path.of(System.getProperty("java.io.tmpdir"), "tiger_ls").toFile().getAbsolutePath();
          log.info(
              "Defaulting to temp folder '{}' as working directory for server {}",
              folder,
              getServerId());
        }
      }
    }
    getConfiguration().getExternalJarOptions().setWorkingDir(folder);
    File f = new File(folder);
    if (!f.exists() && !f.mkdirs()) {
      throw new TigerTestEnvException("Unable to create working dir folder " + f.getAbsolutePath());
    }
    assertCfgPropertySet(getConfiguration(), "healthcheckUrl");
  }

  @Override
  public void performStartup() {
    final String workingDir;
    final CfgExternalJarOptions externalJarOptions = getConfiguration().getExternalJarOptions();
    if (externalJarOptions != null) {
      workingDir = getConfiguration().getExternalJarOptions().getWorkingDir();
    } else {
      workingDir = new File(".").getAbsolutePath();
    }
    setStatus(
        TigerServerStatus.STARTING,
        "Starting external jar instance " + getServerId() + " in folder '" + workingDir + "'...");

    var jarUrl = getConfiguration().getSource().get(0);
    jarFile =
        getTigerTestEnvMgr()
            .getDownloadManager()
            .downloadJarAndReturnFile(this, jarUrl, workingDir);

    List<String> options = new ArrayList<>();
    String javaExe = findJavaExecutable();
    options.add(javaExe);
    if (externalJarOptions != null && externalJarOptions.getOptions() != null) {
      options.addAll(
          externalJarOptions.getOptions().stream()
              .map(getTigerTestEnvMgr()::replaceSysPropsInString)
              .toList());
    }
    options.add("-jar");
    options.add(jarFile.getAbsolutePath());
    if (externalJarOptions != null && externalJarOptions.getArguments() != null) {
      options.addAll(externalJarOptions.getArguments());
    }
    statusMessage(
        "Running '"
            + String.join(" ", options)
            + "' in folder '"
            + new File(workingDir).getAbsolutePath()
            + "'");
    processStartTime = now();
    getTigerTestEnvMgr()
        .getCachedExecutor()
        .submit(
            () -> {
              try {
                statusMessage("Starting Jar process for " + getServerId());
                final ProcessBuilder processBuilder =
                    new ProcessBuilder()
                        .command(options.toArray(String[]::new))
                        .directory(new File(workingDir))
                        .redirectErrorStream(true);
                applyEnvPropertiesToProcess(processBuilder);
                processReference.set(processBuilder.start());
                new TigerStreamLogFeeder(
                    getServerId(), log, processReference.get().getInputStream(), Level.INFO);
                new TigerStreamLogFeeder(
                    getServerId(), log, processReference.get().getErrorStream(), Level.ERROR);
                statusMessage(
                    "Started JAR-File for "
                        + getServerId()
                        + " with PID '"
                        + processReference.get().pid()
                        + "'");
                updateGlobalServerMap(
                    buildHealthcheckUrl().getPort(),
                    processReference.get().pid(),
                    this.getServerId());
              } catch (Exception t) {
                log.error("Failed to start process", t);
                startupException.set(t);
              }
              log.debug("Proc set in atomic var {}", processReference.get());
            });

    if (isHealthCheckNone()) {
      log.warn(
          "Healthcheck for {} is not configured, so unable to add route to local proxy!",
          getServerId());
    } else {
      addServerToLocalProxyRouteMap(buildHealthcheckUrl());
      publishNewStatusUpdate(
          TigerServerStatusUpdate.builder().baseUrl(extractBaseUrl(buildHealthcheckUrl())).build());
    }

    if (startupException.get() != null) {
      throw new TigerTestEnvException(
          "Unable to start external jar '" + getServerId() + "'!", startupException.get());
    }
    waitForServerUp();
  }

  @Override
  public TigerServerStatus updateStatus(boolean quiet) {
    if (processReference.get() == null) {
      setStatus(TigerServerStatus.NEW, "No Jar process found. Waiting...");
      return getStatus();
    } else {
      if (!processReference.get().isAlive()) {
        log.warn("Process {} for {} is stopped!", processReference.get().pid(), getServerId());
        setStatus(
            TigerServerStatus.STOPPED,
            "Jar process for " + getServerId() + " stopped unexpectedly");
        if (now().isBefore(processStartTime.plusSeconds(3))) {
          log.warn(
              "{}: Unusually short process run time ({})! Suspecting defunct jar! (Exitcode={})",
              getServerId(),
              Duration.between(now(), processStartTime),
              processReference.get().exitValue());
          cleanupDefunctJar();
        }
        return getStatus();
      } else {
        return super.updateStatus(false);
      }
    }
  }

  private void cleanupDefunctJar() {
    if (!getConfiguration().getSource().get(0).startsWith(LOCAL) && jarFile.exists()) {
      try {
        Files.delete(jarFile.toPath());
      } catch (IOException e) {
        throw new TigerTestEnvException(
            "Unable to delete jar file " + jarFile.getAbsolutePath(), e);
      }
      if (jarFile.exists()) {
        log.warn("Unable to delete jar file {}", jarFile.getAbsolutePath());
      }
    }
  }

  @Override
  public void shutdown() {
    log.info("Stopping external jar {}...", getServerId());
    stopExternalProcess();
    log.info("Shutdown of external jar {} complete with status {}", getServerId(), getStatus());
  }

  private void stopExternalProcess() {
    if (processReference.get() != null) {
      log.info("Stopping external process (pid={})", processReference.get().pid());
      log.info("Interrupting threads...");
      processReference.get().destroy();
      log.info("Stopping threads...");
      processReference.get().destroyForcibly();
      setStatus(TigerServerStatus.STOPPED, "Jar process for " + getServerId() + " stopped");
    } else {
      setStatus(TigerServerStatus.STOPPED, "No Jar process for " + getServerId() + " found");
    }
  }

  private String findJavaExecutable() {
    final String javaHomeDirectory =
        TigerGlobalConfiguration.readStringOptional("tiger.lib.javaHome")
            .or(() -> TigerGlobalConfiguration.readStringOptional("java.home"))
            .orElseThrow(
                () ->
                    new TigerEnvironmentStartupException(
                        "Could not determine java-home. Expected either 'tiger.lib.javaHome' or"
                            + " 'java.home' to be set, but neither was!"));
    if (System.getProperty("os.name").startsWith("Win")) {
      return javaHomeDirectory + File.separator + "bin" + File.separator + "java.exe";
    } else {
      return javaHomeDirectory + File.separator + "bin" + File.separator + "java";
    }
  }
}
