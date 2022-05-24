/*
 * ${GEMATIK_COPYRIGHT_STATEMENT}
 */

package de.gematik.test.tiger.testenvmgr.env;

import com.google.common.util.concurrent.Monitor;
import de.gematik.test.tiger.common.data.config.CfgExternalJarOptions;
import de.gematik.test.tiger.common.util.TigerSerializationUtil;
import de.gematik.test.tiger.testenvmgr.exceptions.TigerDownloadManagerException;
import de.gematik.test.tiger.testenvmgr.servers.ExternalJarServer;
import de.gematik.test.tiger.testenvmgr.util.TigerEnvironmentStartupException;
import de.gematik.test.tiger.testenvmgr.util.TigerTestEnvException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import kong.unirest.Unirest;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;

@Slf4j
public class DownloadManager {

    private static final String DOWNLOAD_PROPERTIES_SUFFIX = ".dwnProps";
    private static final Set<File> RESERVED_FILES = ConcurrentHashMap.newKeySet();
    private static final Set<String> DOWNLOADING_URLS = ConcurrentHashMap.newKeySet();
    private final Monitor monitor = new Monitor();

    private static Stream<Path> streamOfCandidateFiles(CfgExternalJarOptions externalJarOptions, String jarName) {
        try {
            return Files.walk(Path.of(externalJarOptions.getWorkingDir()), 1)
                .filter(path -> path.getFileName().startsWith(jarName))
                .filter(path -> !path.getFileName().endsWith(DOWNLOAD_PROPERTIES_SUFFIX));
        } catch (IOException e) {
            throw new TigerDownloadManagerException("IO-Error during jar-downloading", e);
        }
    }

    private static boolean isJarDownloadedFromUrl(Path path, String downloadUrl) {
        return readAssociatedFileProperties(path.toFile())
            .map(FileDownloadProperties::getDownloadUrl)
            .map(url -> url.equals(downloadUrl))
            .orElse(false);
    }

    private static File seekNewUniqueFile(CfgExternalJarOptions externalJarOptions, String jarName) {
        synchronized (RESERVED_FILES) {
            if (streamOfCandidateFiles(externalJarOptions, jarName)
                .findAny().isEmpty()) {
                return Path.of(externalJarOptions.getWorkingDir(), jarName).toFile();
            } else {
                AtomicReference<File> candidateFile = new AtomicReference<>();
                do {
                    candidateFile.set(Path.of(externalJarOptions.getWorkingDir(),
                            jarName + "_" + RandomStringUtils.randomAlphanumeric(10))//NOSONAR
                        .toFile());
                } while (streamOfCandidateFiles(externalJarOptions, jarName)
                    .anyMatch(path -> path.getFileName().equals(candidateFile.get().toPath())));

                RESERVED_FILES.add(candidateFile.get());
                return candidateFile.get();
            }
        }
    }

    @SneakyThrows
    private static Optional<FileDownloadProperties> readAssociatedFileProperties(File candidateFile) {
        File propertiesFile = new File(candidateFile.getAbsolutePath() + DOWNLOAD_PROPERTIES_SUFFIX);
        if (!propertiesFile.exists()) {
            return Optional.empty();
        } else {
            return Optional.of(TigerSerializationUtil.fromJson(
                FileUtils.readFileToString(propertiesFile, StandardCharsets.UTF_8),
                FileDownloadProperties.class
            ));
        }
    }

    private static void downloadJar(CfgExternalJarOptions externalJarOptions, String jarUrl, File jarFile, String
        serverId) {
        log.info("Downloading jar for external server {} from '{}'...", serverId, jarUrl);

        var workDir = new File(externalJarOptions.getWorkingDir());
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new TigerTestEnvException("Unable to create working directory " + workDir.getAbsolutePath());
        }

        AtomicReference<LocalDateTime> lastTimePrinted = new AtomicReference<>(LocalDateTime.now());
        AtomicReference<Long> lastSizePrinted = new AtomicReference<>(0L);
        LocalDateTime firstTimePrinted = LocalDateTime.now();

        Unirest.get(jarUrl)
            .downloadMonitor((field, fileName, bytesWritten, totalBytes) -> {
                if (lastTimePrinted.get().isBefore(LocalDateTime.now().minusSeconds(2))
                    || (bytesWritten - 10_000_000) > lastSizePrinted.get()) {
                    final Duration downloadDuration = Duration.between(firstTimePrinted, LocalDateTime.now());
                    var speedInBytesPerMilliSecond = ((double) bytesWritten / downloadDuration.toMillis());
                    var remainingTime = Duration.ofMillis(
                        (long) ((totalBytes - bytesWritten) / speedInBytesPerMilliSecond));
                    log.info(
                        "Downloading jar for {}. {} kb of {} kb completed (Elapsed time {}, estimated {} till completion)",
                        serverId, bytesWritten / 1000, totalBytes / 1000,
                        prettyPrintDuration(downloadDuration), prettyPrintDuration(remainingTime));
                    lastTimePrinted.set(LocalDateTime.now());
                    lastSizePrinted.set(bytesWritten);
                }
            })
            .asFile(jarFile.getAbsolutePath())
            .ifSuccess(
                downloadResponse -> {
                    try {
                        FileUtils.writeByteArrayToFile(
                            new File(jarFile.getAbsolutePath() + DOWNLOAD_PROPERTIES_SUFFIX),
                            generateDownloadPropertiesFile(jarUrl));
                    } catch (IOException e) {
                        throw new TigerEnvironmentStartupException("Error during local saving of jar-file", e);
                    }
                })
            .ifFailure(errorResponse -> {
                throw new TigerEnvironmentStartupException(
                    "Error during jar-file download (status " + errorResponse.getStatus() + ")");
            });
    }

    private static String prettyPrintDuration(Duration duration) {
        return Duration.ofSeconds(duration.toSeconds()).toString()
            .substring(2)
            .replaceAll("(\\d[HMS])(?!$)", "$1 ")
            .toLowerCase();
    }

    private static byte[] generateDownloadPropertiesFile(String url) {
        return TigerSerializationUtil.toJson(FileDownloadProperties.builder()
                .downloadUrl(url)
                .build())
            .getBytes(StandardCharsets.UTF_8);
    }

    public File downloadJarAndReturnFile(ExternalJarServer externalJarServer, String jarUrl) {
        if (jarUrl.startsWith("local:")) {
            var jarName = jarUrl.replaceFirst("local:", "").split("/");
            var jarFile = Paths.get(
                externalJarServer.getConfiguration().getExternalJarOptions().getWorkingDir(),
                jarName[jarName.length - 1]).toFile();
            if (!jarFile.exists()) {
                throw new TigerTestEnvException("Local jar " + jarFile.getAbsolutePath() + " not found!");
            }
            externalJarServer.statusMessage(
                "Starting " + externalJarServer.getServerId() + " from local JAR-File '" + jarFile.getAbsolutePath() + "'");
            return jarFile;
        } else {
            externalJarServer.statusMessage("Downloading " + externalJarServer.getServerId() + " JAR-File from '" + jarUrl + "'...");
            return executeDownload(externalJarServer.getConfiguration().getExternalJarOptions(), jarUrl,
                externalJarServer.getServerId());
        }
    }

    @SneakyThrows
    private File executeDownload(CfgExternalJarOptions externalJarOptions, String jarUrl,
        String serverId) {
        var jarName = jarUrl
            .substring(jarUrl.lastIndexOf("/") + 1)
            .replaceAll("\\W+", "");

        Monitor.Guard jarCurrentlyNotDownloading = monitor.newGuard(
            () -> !DOWNLOADING_URLS.contains(jarUrl));
        log.trace("{} tries to enter the monitor...", serverId);
        synchronized (monitor) {
            monitor.enterWhen(jarCurrentlyNotDownloading);
        }
        log.trace("{} has entered the monitor!", serverId);

        try {
            return streamOfCandidateFiles(externalJarOptions, jarName)
                .filter(path -> isJarDownloadedFromUrl(path, jarUrl))
                .map(Path::toFile)
                .findAny()
                .orElseGet(() -> {
                    File jarFile = seekNewUniqueFile(externalJarOptions, jarName);

                    downloadJar(externalJarOptions, jarUrl, jarFile, serverId);

                    return jarFile;
                });
        } finally {
            log.trace("{} tries to leave the monitor...", serverId);
            monitor.leave();
            log.trace("{} has left the monitor!", serverId);
        }
    }

    @Data
    @Builder
    public static class FileDownloadProperties {

        private String downloadUrl;
        private String etag;
    }
}
