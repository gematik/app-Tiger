/*
 * Copyright (c) 2022 gematik GmbH
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

import static java.time.LocalDateTime.now;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.CfgExternalJarOptions;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.servers.log.TigerStreamLogFeeder;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Builder;
import org.slf4j.event.Level;

public class ExternalJarServer extends AbstractExternalTigerServer {

    private final AtomicReference<Process> processReference = new AtomicReference<>();
    private File jarFile;
    private LocalDateTime processStartTime;

    @Builder
    ExternalJarServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public void performStartup() {
        final String workingDir = getConfiguration().getExternalJarOptions().getWorkingDir();
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(ServerType.EXTERNALJAR)
            .statusMessage("Starting external jar instance " + getServerId() + " in folder '" + workingDir + "'...")
            .build());

        final CfgExternalJarOptions externalJarOptions = getConfiguration().getExternalJarOptions();

        var jarUrl = getConfiguration().getSource().get(0);

        jarFile = getTigerTestEnvMgr().getDownloadManager().downloadJarAndReturnFile(this, jarUrl);

        List<String> options = new ArrayList<>();
        String javaExe = findJavaExecutable();
        options.add(javaExe);
        options.addAll(externalJarOptions.getOptions().stream()
            .map(getTigerTestEnvMgr()::replaceSysPropsInString)
            .collect(Collectors.toList()));
        options.add("-jar");
        options.add(jarFile.getName());
        options.addAll(externalJarOptions.getArguments());
        statusMessage("Running '" + String.join(" ", options)
            + "' in folder '" + new File(workingDir).getAbsolutePath() + "'");

        final AtomicReference<Throwable> exception = new AtomicReference<>();

        processStartTime = now();
        getTigerTestEnvMgr().getExecutor().submit(() -> {
            try {
                statusMessage("Starting Jar process for " + getServerId());
                final ProcessBuilder processBuilder = new ProcessBuilder()
                    .command(options.toArray(String[]::new))
                    .directory(new File(workingDir))
                    .inheritIO();

                processBuilder.environment().putAll(getEnvironmentProperties().stream()
                    .map(str -> str.split("=", 2))
                    .filter(ar -> ar.length == 2)
                    .collect(Collectors.toMap(
                        ar -> ar[0].trim(),
                        ar -> ar[1].trim()
                    )));

                processReference.set(processBuilder.start());
                new TigerStreamLogFeeder(log, processReference.get().getInputStream(), Level.INFO);
                new TigerStreamLogFeeder(log, processReference.get().getErrorStream(), Level.ERROR);
                statusMessage("Started JAR-File for " + getServerId() + " with PID '" + processReference.get().pid() + "'");
            } catch (Throwable t) {
                log.error("Failed to start process", t);
                exception.set(t);
            }
            log.debug("Proc set in atomic var {}", processReference.get());
        });

        if (isHealthCheckNone()) {
            log.warn("Healthcheck for {} is not configured, so unable to add route to local proxy!", getServerId());
        } else {
            addServerToLocalProxyRouteMap(buildHealthcheckUrl());
            publishNewStatusUpdate(TigerServerStatusUpdate.builder()
                .baseUrl(extractBaseUrl(buildHealthcheckUrl()))
                .build());
        }

        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar '" + getServerId() + "'!", exception.get());
        }
        waitForService(true);
        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar '" + getServerId() + "'!", exception.get());
        } else if (getStatus() == TigerServerStatus.STOPPED) {
            throw new TigerEnvironmentStartupException("Unable to start external jar '" + getServerId() + "'!");
        } else if (getStatus() == TigerServerStatus.STARTING) {
            waitForService(false);
            if (exception.get() != null) {
                throw new TigerTestEnvException("Unable to start external jar '" + getServerId() + "'!",
                    exception.get());
            } else {
                throw new TigerTestEnvException("Unable to start external jar '" + getServerId() + "'!");
            }
        }
    }

    public TigerServerStatus updateStatus(boolean quiet) {
        if (!processReference.get().isAlive()) {
            log.warn("Process {} for {} is stopped!", processReference.get().pid(), getServerId());
            setStatus(TigerServerStatus.STOPPED, "Jar process for " + getServerId() + " stopped unexpectedly");
            if (now().isBefore(processStartTime.plusSeconds(3))) {
                log.warn("{}: Unusually short process run time ({})! Suspecting defunct jar! (Exitcode={})",
                    getServerId(), Duration.between(now(), processStartTime), processReference.get().exitValue());
                cleanupDefunctJar();
            }
            return getStatus();
        } else {
            return super.updateStatus(quiet);
        }
    }

    private void cleanupDefunctJar() {
        if (!getConfiguration().getSource().get(0).startsWith("local:")
            && jarFile.exists()) {
            if (!jarFile.delete()) {
                log.warn("Unable to delete jar file {}", jarFile.getAbsolutePath());
            }
        }
    }

    @Override
    public void shutdown() {
        log.info("Stopping external jar {}...", getServerId());
        removeAllRoutes();
        stopExternalProcess();
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
        final String javaHomeDirectory = TigerGlobalConfiguration.readStringOptional("tiger.lib.javaHome")
                .or(() -> TigerGlobalConfiguration.readStringOptional("java.home"))
                .orElseThrow(() -> new TigerEnvironmentStartupException("Could not determine java-home. "
                    + "Expected either 'tiger.lib.javaHome' oder 'java.home' to be set, but neither was!"));
        if (System.getProperty("os.name").startsWith("Win")) {
            return javaHomeDirectory + File.separator + "bin" + File.separator + "java.exe";
        } else {
            return javaHomeDirectory + File.separator + "bin" + File.separator + "java";
        }
    }
}
