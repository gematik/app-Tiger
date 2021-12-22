package de.gematik.test.tiger.testenvmgr.servers;

import static org.awaitility.Awaitility.await;
import de.gematik.rbellogger.util.RbelAnsiColors;
import de.gematik.test.tiger.common.Ansi;
import de.gematik.test.tiger.common.config.CfgExternalJarOptions;
import de.gematik.test.tiger.testenvmgr.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvException;
import de.gematik.test.tiger.testenvmgr.TigerTestEnvMgr;
import de.gematik.test.tiger.testenvmgr.config.CfgServer;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.awaitility.core.ConditionTimeoutException;

@Slf4j
public class ExternalJarServer extends AbstractExternalTigerServer {

    private final AtomicReference<Process> processReference = new AtomicReference<>();

    @Builder
    ExternalJarServer(String serverId, CfgServer configuration, TigerTestEnvMgr tigerTestEnvMgr) {
        super(determineHostname(configuration, serverId), serverId, configuration, tigerTestEnvMgr);
    }

    @Override
    public void performStartup() {
        final CfgExternalJarOptions externalJarOptions = getConfiguration().getExternalJarOptions();
        final String workingDir = externalJarOptions.getWorkingDir();
        log.info(Ansi.colorize("starting external jar instance {} in folder {}...", RbelAnsiColors.GREEN_BOLD),
            getHostname(), workingDir);

        log.info("preparing check for external jar location...");
        var jarUrl = getConfiguration().getSource().get(0);
        var jarName = jarUrl.substring(jarUrl.lastIndexOf("/") + 1);
        var jarFile = Paths.get(externalJarOptions.getWorkingDir(), jarName).toFile();

        log.info("checking external jar location: {},{},{}", jarUrl, jarName, jarFile.getAbsolutePath());
        if (!jarFile.exists()) {
            if (jarUrl.startsWith("local:")) {
                throw new TigerTestEnvException("Local jar " + jarFile.getAbsolutePath() + " not found!");
            }
            downloadJar(externalJarOptions, jarUrl, jarFile);
        }

        log.info("creating cmd line...");
        List<String> options = externalJarOptions.getOptions().stream()
            .map(getTigerTestEnvMgr()::replaceSysPropsInString)
            .collect(Collectors.toList());
        String javaExe = findJavaExecutable();
        options.add(0, javaExe);
        options.add("-jar");
        options.add(jarName);
        options.addAll(externalJarOptions.getArguments());
        log.info("executing '" + String.join(" ", options));
        log.info("in working dir: " + new File(externalJarOptions.getWorkingDir()).getAbsolutePath());
        RuntimeException throwing = null;
        try {
            final AtomicReference<Throwable> exception = new AtomicReference<>();
            getTigerTestEnvMgr().getExecutor().submit(() -> {
                try {
                    processReference.set(new ProcessBuilder()
                        .command(options.toArray(String[]::new))
                        .directory(new File(externalJarOptions.getWorkingDir()))
                        .inheritIO()
                        .start());
                    log.info("New process started (pid={})", processReference.get().pid());
                } catch (Throwable t) {
                    log.error("Failed to start process", t);
                    exception.set(t);
                }
                log.debug("Proc set in atomic var {}", processReference.get());
            });

            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopExternalProcess()));

            if (externalJarOptions.getHealthcheck().equals("NONE")) {
                log.warn("Healthcheck is configured as NONE, so unable to add route to local proxy!");
            } else {
                addServerToLocalProxyRouteMap(getHealthcheckUrl());
            }

            if (exception.get() != null) {
                throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
            }
            if (waitForService(true)) {
                if (exception.get() != null) {
                    throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
            } else {
                if (exception.get() != null) {
                    throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
                waitForService(false);
                if (exception.get() != null) {
                    throwing = new TigerTestEnvException("Unable to start external jar!", exception.get());
                }
            }
        } finally {
            log.info("proc: {}", processReference.get());
            if (processReference.get() != null) {
                if (processReference.get().isAlive()) {
                    log.info("Started {}", getHostname());
                } else if (processReference.get().exitValue() == 0) {
                    log.info("Process exited already {}", getHostname());
                } else {
                    try {
                        final String processOutput = IOUtils.toString(processReference.get().getInputStream(), StandardCharsets.UTF_8);
                        log.warn("Unclear process state {}", processReference);
                        log.info("Output from cmd: {}", processOutput);
                    } catch (IOException e) {
                        throw new TigerEnvironmentStartupException("Error while reading output from external Jar " + getHostname(), e);
                    }
                }
            } else {
                if (throwing == null) {
                    throwing = new TigerTestEnvException("External Jar startup failed");
                } else {
                    throwing = new TigerTestEnvException("External Jar startup failed", throwing);
                }
            }
        }
        if (throwing != null) {
            throw throwing;
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
        } else {
            log.warn("Process for server {} not found... No need to shutdown", getHostname());
        }
    }

    private void downloadJar(CfgExternalJarOptions externalJarOptions, String jarUrl, File jarFile) {
        log.info("downloading jar for external server from '{}'...", jarUrl);
        var workDir = new File(externalJarOptions.getWorkingDir());
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new TigerTestEnvException("Unable to create working directory " + workDir.getAbsolutePath());
        }
        var finished = new AtomicBoolean(false);
        var exception = new AtomicReference<Exception>();
        String totalLength;
        try {
            totalLength = " of " + new URL(jarUrl).openConnection().getContentLength() / 1000 + " kb";
        } catch (IOException e) {
            totalLength = " (total size unknown)";
        }

        var t = new Thread(() -> {
            try {
                FileUtils.copyURLToFile(new URL(jarUrl), jarFile);
                finished.set(true);
            } catch (IOException ioe) {
                log.warn("Failed to copy external jar", ioe);
                exception.set(ioe);
            }
        });

        t.start();
        AtomicInteger progressCtr = new AtomicInteger();
        try {
            String finalTotalLength = totalLength;
            final int pollDelayInMs = 500;
            final int cyclesFor10Secs = 10_000 / pollDelayInMs;
            await().atMost(15, TimeUnit.MINUTES)
                .pollDelay(pollDelayInMs, TimeUnit.MILLISECONDS)
                .until(() -> {
                    progressCtr.getAndIncrement();
                    if (progressCtr.get() == cyclesFor10Secs) {
                        log.info("downloaded jar for {}  {} kb {}",
                            getHostname(), jarFile.length() / 1000, finalTotalLength);
                        progressCtr.set(0);
                    }
                    if (exception.get() != null) {
                        throw new TigerTestEnvException("Failure while downloading jar " + jarUrl + "!",
                            exception.get());
                    }

                    return finished.get();
                });
        } catch (ConditionTimeoutException cte) {
            throw new TigerTestEnvException("Download of " + jarUrl + " took longer then 15 minutes!");
        }
    }

    private String findJavaExecutable() {
        String[] paths = System.getenv("PATH").split(SystemUtils.IS_OS_WINDOWS ? ";" : ":");
        String javaProg = "java" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");
        return Arrays.stream(paths)
            .map(path -> Path.of(path, javaProg).toFile())
            .filter(file -> file.exists() && file.canExecute())
            .map(File::getAbsolutePath)
            .findAny()
            .orElseThrow(() -> new TigerTestEnvException("Unable to find executable java program in PATH"));
    }
}
