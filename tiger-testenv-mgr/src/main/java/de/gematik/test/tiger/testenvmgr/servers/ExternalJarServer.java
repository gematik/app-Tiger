/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.servers;

import static java.time.LocalDateTime.now;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.ServerType;
import de.gematik.test.tiger.common.config.TigerGlobalConfiguration;
import de.gematik.test.tiger.common.data.config.CfgExternalJarOptions;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import de.gematik.test.tiger.testenvmgr.env.TigerServerStatusUpdate;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;

@Slf4j
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
        publishNewStatusUpdate(TigerServerStatusUpdate.builder()
            .type(ServerType.EXTERNALJAR)
            .build());

        final CfgExternalJarOptions externalJarOptions = getConfiguration().getExternalJarOptions();
        final String workingDir = getConfiguration().getExternalJarOptions().getWorkingDir();
        log.info(Ansi.colorize("starting external jar instance {} in folder {}...", RbelAnsiColors.GREEN_BOLD),
            getHostname(), workingDir);

        log.info("preparing check for external jar location...");
        var jarUrl = getConfiguration().getSource().get(0);

        jarFile = getTigerTestEnvMgr().getDownloadManager().downloadJarAndReturnFile(this, jarUrl);

        log.info("creating cmd line...");
        List<String> options = new ArrayList<>();
        String javaExe = findJavaExecutable();
        options.add(javaExe);
        options.addAll(externalJarOptions.getOptions().stream()
            .map(getTigerTestEnvMgr()::replaceSysPropsInString)
            .collect(Collectors.toList()));
        options.add("-jar");
        options.add(jarFile.getName());
        options.addAll(externalJarOptions.getArguments());
        statusMessage("About to run '" + String.join(" ", options)
            + "' in folder '" + new File(workingDir).getAbsolutePath() + "'");
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopExternalProcess));

        final AtomicReference<Throwable> exception = new AtomicReference<>();

        processStartTime = now();
        getTigerTestEnvMgr().getExecutor().submit(() -> {
            try {
                statusMessage("Starting local JAR-File");
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
                statusMessage("Started JAR-File with PID '" + processReference.get().pid() + "'");
            } catch (Throwable t) {
                log.error("Failed to start process", t);
                exception.set(t);
            }
            log.debug("Proc set in atomic var {}", processReference.get());
        });

        if (isHealthCheckNone()) {
            log.warn("Healthcheck is not configured, so unable to add route to local proxy!");
        } else {
            addServerToLocalProxyRouteMap(buildHealthcheckUrl());
            publishNewStatusUpdate(TigerServerStatusUpdate.builder()
                .baseUrl(extractBaseUrl(buildHealthcheckUrl()))
                .build());
        }

        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar '" + getHostname() + "'!", exception.get());
        }
        waitForService(true);
        if (exception.get() != null) {
            throw new TigerTestEnvException("Unable to start external jar '" + getHostname() + "'!", exception.get());
        } else if (getStatus() == TigerServerStatus.STOPPED) {
            throw new TigerEnvironmentStartupException("Unable to start external jar '" + getHostname() + "'!");
        } else if (getStatus() == TigerServerStatus.STARTING) {
            waitForService(false);
            if (exception.get() != null) {
                throw new TigerTestEnvException("Unable to start external jar '" + getHostname() + "'!",
                    exception.get());
            } else {
                throw new TigerTestEnvException("Unable to start external jar '" + getHostname() + "'!");
            }
        }
    }

    public TigerServerStatus updateStatus(boolean quiet) {
        if (!processReference.get().isAlive()) {
            log.warn("Process {} is stopped!", processReference.get().pid());
            setStatus(TigerServerStatus.STOPPED, "Jar process stopped unexpectedly");
            if (now().isBefore(processStartTime.plusSeconds(3))) {
                log.warn("{}: Unusually short process run time ({})! Suspecting defunct jar! (Exitcode={})",
                    getHostname(), Duration.between(now(), processStartTime), processReference.get().exitValue());
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
        log.info("Stopping external jar {}...", getHostname());
        removeAllRoutes();
        stopExternalProcess();
    }

    private void stopExternalProcess() {
        if (processReference.get() != null) {
            log.info("Stopping external process (pid={})", processReference.get().pid());
            log.info("interrupting threads...");
            processReference.get().destroy();
            log.info("stopping threads...");
            processReference.get().destroyForcibly();
            setStatus(TigerServerStatus.STOPPED, "Jar process stopped");
        } else {
            log.warn("Process for server {} not found... No need to shutdown", getHostname());
            setStatus(TigerServerStatus.STOPPED, "Jar process never started");
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
