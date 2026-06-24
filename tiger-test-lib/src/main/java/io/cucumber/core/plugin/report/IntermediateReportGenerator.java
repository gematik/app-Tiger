/*
 *
 * Copyright 2021-2026 gematik GmbH
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
package io.cucumber.core.plugin.report;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import de.gematik.test.tiger.lib.TigerDirector;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.serenitybdd.core.di.SerenityInfrastructure;
import net.thucydides.core.reports.html.HtmlAggregateStoryReporter;
import net.thucydides.model.reports.ResultChecker;
import net.thucydides.model.reports.TestOutcomes;
import net.thucydides.model.requirements.FileSystemRequirements;
import org.apache.commons.io.FileUtils;

/**
 * Generates intermediate Serenity HTML reports after each feature file completes. This allows
 * developers to view test results while the test suite is still running, both locally and on CI
 * systems like Jenkins.
 *
 * <p>The generation pipeline mirrors what {@code TigerSerenityReportMojo} does at the end of the
 * test run, but works <b>incrementally</b>: a persistent working directory accumulates
 * already-manipulated JSON outcome files. On each report generation cycle only new or changed JSON
 * files are copied into a staging area, manipulated there (resolved step descriptions, multiline
 * docstrings), and then moved into the working directory. The Serenity HTML aggregator then runs on
 * the full working directory. This avoids redundantly re-manipulating all previously processed
 * feature outcomes.
 *
 * <p>Report generation is performed asynchronously on a dedicated thread to avoid slowing down test
 * execution. If a new report generation request arrives while one is already in progress, it will
 * be queued and executed after the current one completes.
 */
@Slf4j
public class IntermediateReportGenerator {

  private static final String JSON_SUFFIX = ".json";
  private static final String PROGRESS_JSON_NAME = "progress.json";
  private static final String DEFAULT_REQUIREMENTS_DIR = "src/test/resources/features";
  private static final String WORKING_DIR_NAME = "intermediateWorkDir";
  private static final String STAGING_DIR_NAME = "intermediateStaging";

  private final Path reportDirectory;
  private final Path requirementsBaseDir;
  private final ExecutorService reportExecutor;
  private final ScheduledExecutorService timeoutScheduler;
  private final AtomicInteger completedFeatureCount = new AtomicInteger(0);
  private final Instant startTime;

  /**
   * Tracks the last-modified timestamp of each JSON file that has already been copied and
   * manipulated into the working directory. Uses ConcurrentHashMap since multiple report generation
   * threads may access it concurrently.
   */
  private final Map<String, Long> processedFileTimestamps = new ConcurrentHashMap<>();

  public IntermediateReportGenerator() {
    this(
        SerenityInfrastructure.getConfiguration().getOutputDirectory().toPath(),
        Path.of(DEFAULT_REQUIREMENTS_DIR));
  }

  public IntermediateReportGenerator(Path reportDirectory, Path requirementsBaseDir) {
    this.reportDirectory = reportDirectory;
    this.requirementsBaseDir = requirementsBaseDir;
    this.reportExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "tiger-intermediate-report-generator");
              t.setDaemon(true);
              return t;
            });
    this.timeoutScheduler =
        Executors.newScheduledThreadPool(
            1,
            r -> {
              Thread t = new Thread(r, "tiger-report-timeout-scheduler");
              t.setDaemon(true);
              return t;
            });
    this.startTime = Instant.now();
  }

  /**
   * Called when a feature file has completed execution. Starts a background thread to wait for and
   * generate intermediate reports. The thread waits indefinitely for new JSON files to appear,
   * generates a report once they do, and then exits.
   *
   * @param completedFeatureUri the URI of the feature file that just completed
   */
  public void onFeatureCompleted(URI completedFeatureUri) {
    int count = completedFeatureCount.incrementAndGet();
    Duration elapsed = Duration.between(startTime, Instant.now());
    Duration avgPerFeature = elapsed.dividedBy(count);

    log.info(
        "Feature file completed ({} so far, avg {}/feature): {}. Submitting intermediate report"
            + " generation task to executor.",
        count,
        formatDuration(avgPerFeature),
        completedFeatureUri);

    try {
      reportExecutor.submit(this::waitForJsonFilesAndGenerateReport);
      log.debug("Report generation task submitted to executor");
    } catch (Exception e) {
      log.warn("Failed to submit report generation task to executor", e);
    }
  }

  /**
   * Waits indefinitely for new JSON files to become available and generates a report. This method
   * blocks until new JSON files are detected, then generates the report and returns. Only accessed
   * from the single report-executor thread.
   */
  private void waitForJsonFilesAndGenerateReport() {
    try {
      if (!reportDirectory.toFile().exists()) {
        log.warn(
            "Report directory does not exist yet: {}. Skipping intermediate report generation.",
            reportDirectory);
        return;
      }

      log.info("Waiting for new JSON files in directory: {}", reportDirectory.toAbsolutePath());

      final long waitTimeoutMs = 30_000;
      if (waitForJsonFilesAvailable(reportDirectory, waitTimeoutMs)) {
        log.info("New JSON files detected, generating report...");
        // Just small delay for stabilization - rely on copy retry logic for file locking
        Thread.sleep(200);

        generateReportWithTimeout();
      } else {
        log.debug(
            "No JSON files appeared within {}ms timeout. Task exiting gracefully.", waitTimeoutMs);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("Report generation task was interrupted");
    } catch (Exception e) {
      log.warn("Failed to wait for or generate intermediate report", e);
    }
  }

  /**
   * Generates report with a timeout. If report generation takes too long (more than 60 seconds),
   * the thread will be interrupted to prevent blocking the test suite indefinitely. Any remaining
   * JSON files will be processed during the final shutdown.
   */
  private void generateReportWithTimeout() {
    final long timeoutSeconds = 120;
    final long reportStartTime = System.currentTimeMillis();
    final Thread currentThread = Thread.currentThread();

    ScheduledFuture<?> timeoutTask =
        timeoutScheduler.schedule(
            () -> {
              log.warn(
                  "Report generation exceeded {}s timeout, interrupting thread", timeoutSeconds);
              currentThread.interrupt();
            },
            timeoutSeconds,
            TimeUnit.SECONDS);

    try {
      generateReport();
      long elapsedMs = System.currentTimeMillis() - reportStartTime;
      log.info("Report generation completed in {}ms", elapsedMs);
    } catch (Exception e) {
      long elapsedMs = System.currentTimeMillis() - reportStartTime;
      if (Thread.currentThread().isInterrupted()) {
        log.warn(
            "Report generation was interrupted after {}ms due to timeout. "
                + "Remaining JSON files will be processed during shutdown.",
            elapsedMs);
      } else {
        log.warn("Report generation failed after {}ms: {}", elapsedMs, e.getMessage());
      }
    } finally {
      timeoutTask.cancel(false);
    }
  }

  /**
   * Waits for new JSON files to become available in the report directory using a file system
   * watcher. Returns as soon as new files are detected, or after timeout expires.
   *
   * @param sourceDir the source directory to monitor
   * @param timeoutMs maximum time to wait in milliseconds
   * @return true if new JSON files were found, false if timeout expired
   */
  private boolean waitForJsonFilesAvailable(Path sourceDir, long timeoutMs) {
    final long waitStartTime = System.currentTimeMillis();

    try (WatchService watchService = sourceDir.getFileSystem().newWatchService()) {
      sourceDir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
      log.debug("Registered WatchService for directory: {}", sourceDir.toAbsolutePath());

      int searchDepth = getJsonFileSearchDepth();
      if (hasNewJsonFilesRecursive(sourceDir, searchDepth)) {
        log.debug("New JSON files already exist (recursive check)");
        return true;
      }

      while (System.currentTimeMillis() - waitStartTime < timeoutMs) {
        long remainingTimeMs = timeoutMs - (System.currentTimeMillis() - waitStartTime);
        if (remainingTimeMs <= 0) {
          log.debug("Timeout waiting for JSON files (no time remaining)");
          return false;
        }

        WatchKey key = watchService.poll(Math.min(remainingTimeMs, 5000), TimeUnit.MILLISECONDS);
        if (key == null) {
          long elapsed = System.currentTimeMillis() - waitStartTime;
          if (elapsed >= timeoutMs) {
            log.debug("File watcher timeout expired after {}ms", elapsed);
            return false;
          }
          continue;
        }

        if (processWatchKey(key)) {
          return true;
        }
      }

      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.debug("File watching interrupted");
      return false;
    } catch (IOException e) {
      log.debug("Error setting up WatchService: {}", e.getMessage());
      return hasNewJsonFiles(sourceDir);
    }
  }

  private static int getJsonFileSearchDepth() {
    int searchDepth = 3;
    try {
      if (TigerDirector.isSerenityAvailable(true)) {
        searchDepth = Math.max(1, TigerDirector.getLibConfig().getIntermediateReportSearchDepth());
      }
    } catch (RuntimeException e) {
      log.debug("Could not read intermediate report search depth config, using default", e);
    }
    return searchDepth;
  }

  /** Processes file system watch events and returns true if a new JSON file was detected. */
  private boolean processWatchKey(WatchKey key) {
    for (WatchEvent<?> event : key.pollEvents()) {
      if (event.context() instanceof Path changedFile && isNewJsonFile(changedFile)) {
        log.debug("New JSON file detected via WatchService: {}", changedFile);
        key.reset();
        return true;
      }
    }
    key.reset();
    return false;
  }

  /** Checks if there are new JSON files (not yet processed) in the directory. */
  private boolean hasNewJsonFiles(Path sourceDir) {
    try (Stream<Path> files = Files.list(sourceDir)) {
      return files.anyMatch(this::isNewJsonFile);
    } catch (IOException e) {
      log.debug("Error checking for JSON files: {}", e.getMessage());
      return false;
    }
  }

  /** Checks if a single file is a new JSON file that hasn't been processed yet. */
  private boolean isNewJsonFile(Path filePath) {
    String fileName = filePath.getFileName().toString();
    return fileName.endsWith(JSON_SUFFIX)
        && !fileName.startsWith(PROGRESS_JSON_NAME)
        && !processedFileTimestamps.containsKey(fileName);
  }

  /**
   * Recursive check for any new JSON files (not yet processed) within sourceDir up to the given
   * search depth. This complements the non-recursive WatchService approach which only monitors the
   * immediate directory entries.
   */
  private boolean hasNewJsonFilesRecursive(Path sourceDir, int searchDepth) {
    try (Stream<Path> files = Files.walk(sourceDir, searchDepth)) {
      return files.anyMatch(
          p ->
              p.toString().endsWith(JSON_SUFFIX)
                  && !p.getFileName().toString().equals(PROGRESS_JSON_NAME)
                  && !processedFileTimestamps.containsKey(sourceDir.relativize(p).toString()));
    } catch (IOException e) {
      log.debug("Error checking for JSON files recursively: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Generates an intermediate report incrementally:
   *
   * <ol>
   *   <li>Identify new or changed JSON outcome files in the source report directory
   *   <li>Copy only those into a small staging directory and apply step manipulations there
   *   <li>Move the manipulated files from staging into the persistent working directory
   *   <li>Generate the full HTML report from the working directory (which now contains all
   *       previously processed files plus the newly manipulated ones)
   * </ol>
   */
  private void generateReport() {
    try {
      if (!reportDirectory.toFile().exists()) {
        log.warn(
            "Report directory does not exist yet: {}. Skipping intermediate report generation.",
            reportDirectory);
        return;
      }

      log.info("Generating intermediate Serenity HTML report from {}", reportDirectory);
      Instant reportStart = Instant.now();

      Path workingDir = reportDirectory.resolve(WORKING_DIR_NAME);
      Files.createDirectories(workingDir);

      // Step 1+2: Copy only new/changed JSON files to a staging dir and manipulate them
      Path stagingDir = reportDirectory.resolve(STAGING_DIR_NAME);
      int newFiles = copyNewOrChangedJsonFiles(reportDirectory, stagingDir);

      if (newFiles == 0) {
        log.debug(
            "No new or changed JSON files found to process. Skipping report generation for now.");
        return;
      }

      log.debug("Applying step manipulations to {} files...", newFiles);
      SerenityReportFileManipulator.applyAllStepManipulations(stagingDir.toFile());

      log.debug("Moving {} files to working directory...", newFiles);
      moveStagingToWorkingDir(stagingDir, workingDir);
      cleanUpStagingDir(stagingDir);

      log.debug("Generating HTML reports from working directory...");
      HtmlAggregateStoryReporter reporter =
          new HtmlAggregateStoryReporter(
              "default", new FileSystemRequirements(requirementsBaseDir.toString()));
      reporter.setSourceDirectory(workingDir.toFile());
      reporter.setOutputDirectory(reportDirectory.toFile());
      reporter.setGenerateTestOutcomeReports();

      log.debug("Calling Serenity report generator...");
      TestOutcomes outcomes =
          reporter.generateReportsForTestResultsFrom(reporter.getSourceDirectory());
      new ResultChecker(reporter.getOutputDirectory()).checkTestResults(outcomes);

      Duration reportDuration = Duration.between(reportStart, Instant.now());
      Path indexPath = reportDirectory.resolve("index.html");

      log.info(
          "Intermediate report generated in {} ({} test outcomes, {} new JSON files processed)."
              + " View at: {}",
          formatDuration(reportDuration),
          outcomes.getTestCaseCount(),
          newFiles,
          indexPath.toUri());

      writeProgressFile(outcomes);

    } catch (Exception e) {
      log.warn("Failed to generate intermediate Serenity report", e);
    }
  }

  private int copyNewOrChangedJsonFiles(Path sourceDir, Path stagingDir) throws IOException {
    cleanUpStagingDir(stagingDir);
    Files.createDirectories(stagingDir);

    int count = 0;
    try (Stream<Path> files = Files.list(sourceDir)) {
      for (Path path : files.filter(p -> p.toString().endsWith(JSON_SUFFIX)).toList()) {
        String fileName = path.getFileName().toString();
        long lastModified = Files.getLastModifiedTime(path).toMillis();

        Long previousTimestamp = processedFileTimestamps.get(fileName);
        if ((previousTimestamp == null || previousTimestamp != lastModified)
            && copyFileWithRetry(path, stagingDir.resolve(fileName))) {
          processedFileTimestamps.put(fileName, lastModified);
          count++;
        }
      }
    }

    if (count > 0) {
      log.debug("Found {} new/changed JSON outcome files to process", count);
    } else {
      log.debug("No new or changed JSON outcome files found");
    }

    return count;
  }

  /**
   * Attempts to copy a file with retry logic to handle file locking.
   *
   * @param source the source file to copy
   * @param target the target location
   * @return true if copy succeeded, false if it failed after retries
   */
  private boolean copyFileWithRetry(Path source, Path target) {
    final int maxRetries = 5;
    final long retryDelayMs = 100;

    for (int attempt = 0; attempt < maxRetries; attempt++) {
      try {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        if (attempt > 0) {
          log.debug("Successfully copied {} after {} retries", source.getFileName(), attempt);
        }
        return true;
      } catch (IOException e) {
        if (attempt < maxRetries - 1) {
          log.debug(
              "Failed to copy {} (attempt {}/{}), retrying in {}ms: {}",
              source.getFileName(),
              attempt + 1,
              maxRetries,
              retryDelayMs,
              e.getMessage());
          try {
            Thread.sleep(retryDelayMs);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
          }
        } else {
          log.warn(
              "Failed to copy {} after {} attempts: {}",
              source.getFileName(),
              maxRetries,
              e.getMessage());
          return false;
        }
      }
    }
    return false;
  }

  /**
   * Moves all JSON files from the staging directory into the persistent working directory,
   * overwriting any previous versions.
   */
  private void moveStagingToWorkingDir(Path stagingDir, Path workingDir) throws IOException {
    try (Stream<Path> files = Files.list(stagingDir)) {
      files
          .filter(p -> p.toString().endsWith(JSON_SUFFIX))
          .forEach(
              path -> {
                try {
                  Files.move(
                      path,
                      workingDir.resolve(path.getFileName()),
                      StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                  log.warn("Unable to move manipulated JSON file {} to working dir", path, e);
                }
              });
    }
  }

  private void cleanUpStagingDir(Path stagingDir) throws IOException {
    if (stagingDir.toFile().exists()) {
      FileUtils.deleteDirectory(stagingDir.toFile());
    }
  }

  /**
   * Writes a small JSON progress file alongside the report to help CI scripts and developers
   * quickly check the current test execution status without opening the full report.
   */
  private void writeProgressFile(TestOutcomes outcomes) {
    try {
      int completed = completedFeatureCount.get();
      Duration elapsed = Duration.between(startTime, Instant.now());
      Duration avgPerFeature = completed > 0 ? elapsed.dividedBy(completed) : Duration.ZERO;

      String progressJson =
          """
          {
            "completedFeatures": %d,
            "elapsedSeconds": %d,
            "avgSecondsPerFeature": %d,
            "totalOutcomes": %d,
            "passingTests": %d,
            "failingTests": %d,
            "pendingTests": %d,
            "timestamp": "%s"
          }"""
              .formatted(
                  completed,
                  elapsed.getSeconds(),
                  avgPerFeature.getSeconds(),
                  outcomes.getOutcomes().size(),
                  outcomes.getPassingTests().getOutcomes().size(),
                  outcomes.getFailingTests().getOutcomes().size(),
                  outcomes.getPendingTests().getOutcomes().size(),
                  Instant.now().toString());

      Files.writeString(reportDirectory.resolve(PROGRESS_JSON_NAME), progressJson);
    } catch (IOException e) {
      log.debug("Could not write progress file", e);
    }
  }

  /**
   * Shuts down the report executor and scheduler, and waits for any pending report generation to
   * complete. The persistent working directory (intermediateWorkDir) is intentionally kept so that
   * {@code TigerSerenityReportMojo} can reuse the already-manipulated JSON files for generating
   * single-page-html and json-summary reports without re-doing the step manipulations.
   *
   * <p>NOTE: This method is optional. Since the executor uses daemon threads, they will
   * automatically terminate when the JVM exits. This method is provided for explicit cleanup if
   * needed.
   */
  public void shutdown() {
    log.info("Shutting down intermediate report generator...");
    reportExecutor.shutdown();
    timeoutScheduler.shutdown();
    try {
      if (!reportExecutor.awaitTermination(120, TimeUnit.SECONDS)) {
        log.warn("Intermediate report executor did not terminate in time");
        reportExecutor.shutdownNow();
      }
      if (!timeoutScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        log.warn("Report timeout scheduler did not terminate in time");
        timeoutScheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      reportExecutor.shutdownNow();
      timeoutScheduler.shutdownNow();
    }

    // Only clean up the staging directory; keep the working directory for the Mojo
    try {
      Path stagingDir = reportDirectory.resolve(STAGING_DIR_NAME);
      if (stagingDir.toFile().exists()) {
        FileUtils.deleteDirectory(stagingDir.toFile());
      }
    } catch (IOException e) {
      log.debug("Could not clean up intermediate staging directory", e);
    }
  }

  /** Name of the persistent working directory containing already-manipulated JSON files. */
  public static String getWorkingDirName() {
    return WORKING_DIR_NAME;
  }

  private static String formatDuration(Duration duration) {
    long totalSeconds = duration.getSeconds();
    if (totalSeconds < 60) {
      return totalSeconds + "s";
    }
    long minutes = totalSeconds / 60;
    long seconds = totalSeconds % 60;
    return minutes + "m " + seconds + "s";
  }
}
